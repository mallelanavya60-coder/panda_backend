package com.mhs.api.scheduler.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional
public class SchedulerService {

    private DataService dataService;

    public SchedulerService(DataService dataService) {
        this.dataService = dataService;
    }

    public Map<String, Object> generate(int semesterId) {
        System.out.println("=== Generating schedule for semester: " + semesterId + " ===");

        // 1) Ensure sections exist based on demand
        ensureSections(semesterId);

        // 2) Clear previous assignments for these sections
        clearPreviousAssignments(semesterId);

        // 3) Load resources
        List<Map<String, Object>> timeslots = dataService.query("SELECT * FROM timeslots ORDER BY id");
        Map<String, List<Map<String, Object>>> timeslotsByDay = timeslots.stream()
                .collect(Collectors.groupingBy(t -> (String) t.get("day"), LinkedHashMap::new, Collectors.toList()));

        List<Map<String, Object>> rooms = dataService.query("SELECT id, name, room_type_id, capacity FROM classrooms");
        Map<Integer, List<Map<String, Object>>> roomsByType = rooms.stream()
                .collect(Collectors.groupingBy(r -> (Integer) r.getOrDefault("room_type_id", -1)));

        List<Map<String, Object>> teachers = dataService.query("SELECT id, first_name, last_name, specialization_id FROM teachers");
        Map<Integer, List<Map<String, Object>>> teachersBySpec = teachers.stream()
                .collect(Collectors.groupingBy(t -> (Integer) t.getOrDefault("specialization_id", -1)));

        List<Map<String, Object>> sections = dataService.query(
                "SELECT s.*, c.hours_per_week as course_hours, c.specialization_id as course_spec, c.code as course_code, c.name as course_name " +
                        "FROM sections s JOIN courses c ON s.course_id = c.id WHERE s.semester_id = ?", semesterId);

        // occupancy maps
        Map<Integer, Set<Integer>> roomOccupied = new HashMap<>();    // timeslotId -> roomIds
        Map<Integer, Set<Integer>> teacherOccupied = new HashMap<>(); // timeslotId -> teacherIds
        Map<Integer, Map<String, Integer>> teacherDailyHours = new HashMap<>(); // teacherId -> (day->hours)

        List<Integer> assignedSections = new ArrayList<>();
        List<Integer> unscheduledSections = new ArrayList<>();

        // sort by hardness (special room + more hours)
        sections.sort((a, b) -> {
            int sa = Optional.ofNullable((Integer) a.get("course_hours")).orElse(3);
            int sb = Optional.ofNullable((Integer) b.get("course_hours")).orElse(3);
            if (a.get("preferred_room_type_id") != null) sa += 3;
            if (b.get("preferred_room_type_id") != null) sb += 3;
            return Integer.compare(sb, sa);
        });

        lastSectionsById.clear();
        for (Map<String, Object> sec : sections) {
            lastSectionsById.put((Integer) sec.get("id"), sec);
            int secId = (Integer) sec.get("id");
            int hours = Optional.ofNullable((Integer) sec.get("course_hours")).orElse(3);
            int preferredType = sec.get("preferred_room_type_id") == null ? -1 : (Integer) sec.get("preferred_room_type_id");
            int courseSpec = sec.get("course_spec") == null ? -1 : (Integer) sec.get("course_spec");

            List<Map<String, Object>> roomCandidates = roomsByType.getOrDefault(preferredType, rooms);
            List<Map<String, Object>> teacherCandidates = teachersBySpec.getOrDefault(courseSpec, teachers);

            // split hours into sessions (prefer 2h when possible)
            int rem = hours;
            List<Integer> sessionLens = new ArrayList<>();
            while (rem > 0) {
                if (rem >= 2) { sessionLens.add(2); rem -= 2; }
                else { sessionLens.add(1); rem -= 1; }
            }

            boolean allPlaced = true;
            List<Assignment> assignments = new ArrayList<>();

            // schedule each session
            for (int len : sessionLens) {
                boolean placed = false;
                for (var dayEntry : timeslotsByDay.entrySet()) {
                    String day = dayEntry.getKey();
                    List<Map<String, Object>> daySlots = dayEntry.getValue();
                    for (int i = 0; i < daySlots.size(); i++) {
                        if (len == 2 && i + 1 >= daySlots.size()) continue;
                        // ensure we do not schedule across lunch — our timeslots avoid lunch already
                        List<Integer> candidateSlots = (len == 1)
                                ? List.of((Integer) daySlots.get(i).get("id"))
                                : List.of((Integer) daySlots.get(i).get("id"), (Integer) daySlots.get(i + 1).get("id"));

                        // check room + teacher availability for this candidate
                        outer:
                        for (Map<String, Object> room : roomCandidates) {
                            int roomId = (Integer) room.get("id");
                            int cap = Optional.ofNullable((Integer) room.get("capacity")).orElse(10);
                            if (cap < Optional.ofNullable((Integer) sec.get("capacity")).orElse(10)) continue;

                            // room conflict?
                            boolean roomConflict = candidateSlots.stream().anyMatch(ts -> roomOccupied.getOrDefault(ts, Set.of()).contains(roomId));
                            if (roomConflict) continue;

                            for (Map<String, Object> teacher : teacherCandidates) {
                                int tid = (Integer) teacher.get("id");
                                boolean teacherConflict = candidateSlots.stream().anyMatch(ts -> teacherOccupied.getOrDefault(ts, Set.of()).contains(tid));
                                if (teacherConflict) continue;

                                int curr = teacherDailyHours.getOrDefault(tid, new HashMap<>()).getOrDefault(day, 0);
                                if (curr + len > 4) continue;

                                // PASS: assign
                                Assignment a = new Assignment(secId, candidateSlots, roomId, tid);
                                assignments.add(a);
                                // mark occupancy
                                candidateSlots.forEach(ts -> {
                                    roomOccupied.computeIfAbsent(ts, k -> new HashSet<>()).add(roomId);
                                    teacherOccupied.computeIfAbsent(ts, k -> new HashSet<>()).add(tid);
                                });
                                teacherDailyHours.computeIfAbsent(tid, k -> new HashMap<>()).put(day, curr + len);

                                placed = true;
                                break outer;
                            }
                        }
                        if (placed) break;
                    }
                    if (placed) break;
                } // end day search

                if (!placed) { allPlaced = false; break; }
            } // end sessions for section

            if (allPlaced) {
                // persist safely using INSERT OR IGNORE (idempotent)
                for (Assignment a : assignments) {
                    for (int ts : a.timeslotIds) {
                        dataService.update("INSERT OR IGNORE INTO schedule_assignments (section_id,timeslot_id,room_id,teacher_id) VALUES (?,?,?,?)",
                                a.sectionId, ts, a.roomId, a.teacherId);
                    }
                }
                dataService.update("UPDATE sections SET status='scheduled' WHERE id = ?", secId);
                assignedSections.add(secId);
            } else {
                unscheduledSections.add(secId);
            }
        } // end all sections

        // debug preview - first 20 rows for this semester
        List<Map<String, Object>> preview = dataService.query(
                "SELECT sa.section_id, s.section_number, c.code as course_code, c.name as course_name, t.first_name || ' ' || t.last_name as teacher_name, " +
                        "r.name as room_name, ts.day, ts.start_time, ts.end_time " +
                        "FROM schedule_assignments sa " +
                        "JOIN sections s ON s.id = sa.section_id " +
                        "JOIN courses c ON c.id = s.course_id " +
                        "JOIN teachers t ON t.id = sa.teacher_id " +
                        "JOIN classrooms r ON r.id = sa.room_id " +
                        "JOIN timeslots ts ON ts.id = sa.timeslot_id " +
                        "WHERE s.semester_id = ? ORDER BY sa.section_id, ts.day, ts.start_time LIMIT 50",
                semesterId);

        System.out.println("🔍 Schedule preview (up to 50 rows):");
        for (Map<String, Object> r : preview) {
            System.out.printf("   Section %s (%s) - %s | %s %s-%s%n",
                    r.get("section_id"), r.get("course_code"), r.get("teacher_name"), r.get("day"), r.get("start_time"), r.get("end_time"));
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("assigned_count", assignedSections.size());
        result.put("total_sections", sections.size());
        result.put("assigned_sections", assignedSections);
        result.put("unscheduled_sections", unscheduledSections);
        return result;
    }


    private void clearPreviousAssignments(int semesterId) {
            // Fetch all section IDs for the given semester
            List<Map<String, Object>> secs = dataService.query(
                    "SELECT id FROM sections WHERE semester_id = ?", semesterId);

            if (secs.isEmpty()) {
                System.out.println("🟡 No existing sections found for semester ID " + semesterId);
                return;
            }

            // Build placeholders (?, ?, ?, ...) for deletion
            String placeholders = secs.stream().map(s -> "?").collect(Collectors.joining(","));
            Object[] ids = secs.stream().map(s -> s.get("id")).toArray();

            // Log which section IDs will be cleared
            System.out.println("🧹 Clearing old schedule assignments for sections: " +
                    Arrays.toString(ids));

            // Delete existing schedule assignments
            int deletedRows = dataService.update(
                    String.format("DELETE FROM schedule_assignments WHERE section_id IN (%s)", placeholders),
                    ids
            );

            // Reset section statuses
            dataService.update("UPDATE sections SET status='unscheduled' WHERE semester_id = ?", semesterId);

            // Log results
            System.out.println("✅ Cleared " + deletedRows + " old schedule assignment rows.");
    }

    private void ensureSections(int semesterId) {
        // Get student demand per course if available
        // We'll try to use student_course_history rows with status 'planned' or 'requested' or 'enrolled'
        Map<Integer, Integer> demandByCourse = new HashMap<>();
        try {
            List<Map<String, Object>> demandRows = dataService.query(
                    "SELECT course_id, COUNT(*) as cnt FROM student_course_history " +
                            "WHERE status IN ('requested','planned','enrolled') GROUP BY course_id");
            for (Map<String, Object> r : demandRows) {
                demandByCourse.put((Integer) r.get("course_id"), ((Number) r.get("cnt")).intValue());
            }
        } catch (Exception ex) {
            // table may not exist or no rows — we'll fallback to heuristic
            System.out.println("⚠️ No explicit demand rows found (student_course_history): " + ex.getMessage());
        }

        // Find existing course_ids already having sections for this semester
        List<Map<String, Object>> existing = dataService.query("SELECT course_id, COUNT(*) as cnt FROM sections WHERE semester_id = ? GROUP BY course_id", semesterId);
        Set<Integer> existingCourseIds = existing.stream().map(r -> (Integer) r.get("course_id")).collect(Collectors.toSet());
        Map<Integer, Integer> existingSectionCount = existing.stream().collect(Collectors.toMap(
                r -> (Integer) r.get("course_id"),
                r -> ((Number) r.get("cnt")).intValue()
        ));

        // Query courses that should be offered this semester (semester_order not null OR all courses if you prefer)
        List<Map<String, Object>> courses = dataService.query("SELECT * FROM courses WHERE semester_order IS NOT NULL OR semester_order = ''");

        final int ROOM_CAPACITY = 10; // as per constraints
        for (Map<String, Object> c : courses) {
            int cid = (Integer) c.get("id");
            int demand = demandByCourse.getOrDefault(cid, 0);

            // If no demand data, estimate default demand: use grade-level population if available
            if (demand == 0) {
                // simple fallback: 10 students per course by default (small), you can adjust
                demand = 10;
            }

            // compute required sections
            int requiredSections = (demand + ROOM_CAPACITY - 1) / ROOM_CAPACITY; // ceil(demand/10)
            int existingSections = existingSectionCount.getOrDefault(cid, 0);
            int toCreate = Math.max(0, requiredSections - existingSections);

            for (int i = 0; i < toCreate; i++) {
                // insert a new section
                int hours = Optional.ofNullable((Integer) c.get("hours_per_week")).orElse(3);
                dataService.insertAndReturnKey(
                        "INSERT INTO sections (course_id, semester_id, section_number, capacity, hours_per_week, preferred_room_type_id) VALUES (?,?,?,?,?,?)",
                        cid, semesterId, existingSections + i + 1, ROOM_CAPACITY, hours, c.get("specialization_id")
                );
            }
        }
    }


    /**
     * Attempt a small local repair/backtrack:
     * - For the provided section (sec) and required session length (len), try to free a candidate by swapping.
     * - This function mutates roomOccupied, teacherOccupied and teacherDailyHours when successful,
     *   and appends created Assignment(s) into 'assignments' list.
     *
     * Returns true if it managed to place the session (and updated 'assignments' accordingly).
     *
     * This is a shallow recursive repair with limited depth to avoid exponential blowup.
     */
    private boolean tryLocalRepair(Map<String, Object> sec, int len,
                                   Map<String, List<Integer>> timeslotIdsByDay,
                                   Map<Integer, List<Map<String, Object>>> roomsByType,
                                   Map<Integer, List<Map<String, Object>>> teachersBySpec,
                                   Map<Integer, Set<Integer>> roomOccupied,
                                   Map<Integer, Set<Integer>> teacherOccupied,
                                   Map<Integer, Map<String, Integer>> teacherDailyHours,
                                   List<Assignment> currentAssignments) {
        // max recursive depth
        final int MAX_DEPTH = 2;
        // try each day and candidate contiguous slot(s) similar to greedy
        int preferredType = sec.get("preferred_room_type_id") == null ? -1 : (Integer) sec.get("preferred_room_type_id");
        int courseSpec = sec.get("course_spec") == null ? -1 : (Integer) sec.get("course_spec");
        List<Map<String, Object>> roomCandidates = roomsByType.getOrDefault(preferredType, Collections.emptyList());
        List<Map<String, Object>> teacherCandidates = teachersBySpec.getOrDefault(courseSpec, Collections.emptyList());
        if (roomCandidates.isEmpty()) roomCandidates = roomsByType.values().stream().flatMap(List::stream).collect(Collectors.toList());
        if (teacherCandidates.isEmpty()) teacherCandidates = teachersBySpec.values().stream().flatMap(List::stream).collect(Collectors.toList());

        // Build quick mapping of timeslot -> existing assignments (in-memory) from currentAssignments
        Map<Integer, List<Assignment>> assignedByTimeslot = new HashMap<>();
        for (Assignment a : currentAssignments) {
            for (int ts : a.timeslotIds) {
                assignedByTimeslot.computeIfAbsent(ts, k -> new ArrayList<>()).add(a);
            }
        }

        // Helper recursive attempt
        List<Map<String, Object>> finalRoomCandidates = roomCandidates;
        List<Map<String, Object>> finalTeacherCandidates = teacherCandidates;
        class RepairAttempt {
            boolean attempt(List<Integer> candidate, int depth) {
                // Try to find a room + teacher pair that is free (naive)
                for (Map<String, Object> room : finalRoomCandidates) {
                    int roomId = (Integer) room.get("id");
                    boolean roomConflict = candidate.stream().anyMatch(ts -> roomOccupied.getOrDefault(ts, Set.of()).contains(roomId));
                    if (roomConflict) {
                        // find assignments blocking the room
                        List<Assignment> blockers = candidate.stream()
                                .flatMap(ts -> assignedByTimeslot.getOrDefault(ts, Collections.emptyList()).stream())
                                .filter(a -> a.roomId == roomId)
                                .collect(Collectors.toList());
                        // try to displace blockers
                        for (Assignment block : blockers) {
                            if (depth >= MAX_DEPTH) continue;
                            if (attemptDisplace(block, depth + 1)) {
                                // after displacement, room should be free for candidate
                                // update occupancy for room
                                for (int ts : candidate) roomOccupied.computeIfAbsent(ts, k->new HashSet<>()).remove(roomId);
                                // continue to teacher checks below
                            } else {
                                // cannot displace blocker; try next room
                                roomConflict = true;
                                break;
                            }
                        }
                        // re-evaluate conflict
                        roomConflict = candidate.stream().anyMatch(ts -> roomOccupied.getOrDefault(ts, Set.of()).contains(roomId));
                        if (roomConflict) continue;
                    }

                    // Now try teachers
                    for (Map<String, Object> teacher : finalTeacherCandidates) {
                        int tid = (Integer) teacher.get("id");
                        boolean teacherConflict = candidate.stream().anyMatch(ts -> teacherOccupied.getOrDefault(ts, Set.of()).contains(tid));
                        if (teacherConflict) {
                            // try to displace teacher's blockers
                            List<Assignment> tblockers = candidate.stream()
                                    .flatMap(ts -> assignedByTimeslot.getOrDefault(ts, Collections.emptyList()).stream())
                                    .filter(a -> a.teacherId == tid)
                                    .collect(Collectors.toList());
                            boolean allDisplaced = true;
                            for (Assignment tblock : tblockers) {
                                if (depth >= MAX_DEPTH || !attemptDisplace(tblock, depth + 1)) {
                                    allDisplaced = false;
                                    break;
                                }
                            }
                            if (!allDisplaced) continue;
                            // teacher cleared
                        }

                        // check teacher daily hours constraint
                        // we need day name for this candidate; we assume candidate slots share same day in this scheduler design
                        // find day from first timeslot id using timeslotIdsByDay map
                        String day = findDayForTimeslot(candidate.get(0), timeslotIdsByDay);
                        int current = teacherDailyHours.getOrDefault(tid, new HashMap<>()).getOrDefault(day, 0);
                        if (current + candidate.size() > 4) continue;

                        // PASS: create assignment (mutate in-memory structures)
                        Assignment newAssign = new Assignment((Integer) sec.get("id"), candidate, roomId, tid);
                        currentAssignments.add(newAssign);
                        for (int ts : candidate) {
                            roomOccupied.computeIfAbsent(ts, k-> new HashSet<>()).add(roomId);
                            teacherOccupied.computeIfAbsent(ts, k-> new HashSet<>()).add(tid);
                        }
                        teacherDailyHours.computeIfAbsent(tid, k-> new HashMap<>()).put(day, current + candidate.size());
                        // update assignedByTimeslot
                        for (int ts : candidate) assignedByTimeslot.computeIfAbsent(ts, k-> new ArrayList<>()).add(newAssign);
                        return true;
                    } // end teacher loop
                } // end room loop
                return false;
            }

            // attempt to displace a blocking assignment by finding alternative slots for it recursively
            boolean attemptDisplace(Assignment block, int depth) {
                // remove block temporarily from in-memory occupancy
                for (int ts : block.timeslotIds) {
                    roomOccupied.getOrDefault(ts, new HashSet<>()).remove(block.roomId);
                    teacherOccupied.getOrDefault(ts, new HashSet<>()).remove(block.teacherId);
                    // also remove from assignedByTimeslot
                    assignedByTimeslot.getOrDefault(ts, new ArrayList<>()).remove(block);
                }
                // try to find another place for block.sectionId
                Map<String, Object> blockedSection = findSectionById(block.sectionId);
                if (blockedSection == null) {
                    // cannot find section, rollback removal
                    for (int ts : block.timeslotIds) {
                        roomOccupied.computeIfAbsent(ts, k-> new HashSet<>()).add(block.roomId);
                        teacherOccupied.computeIfAbsent(ts, k-> new HashSet<>()).add(block.teacherId);
                        assignedByTimeslot.computeIfAbsent(ts, k-> new ArrayList<>()).add(block);
                    }
                    return false;
                }
                // attempt to place block elsewhere (simple greedy search similar to top-level but depth aware)
                boolean placed = attemptRelocateSection(blockedSection, block, depth);
                if (!placed) {
                    // rollback: restore original occupancy/assignment
                    for (int ts : block.timeslotIds) {
                        roomOccupied.computeIfAbsent(ts, k-> new HashSet<>()).add(block.roomId);
                        teacherOccupied.computeIfAbsent(ts, k-> new HashSet<>()).add(block.teacherId);
                        assignedByTimeslot.computeIfAbsent(ts, k-> new ArrayList<>()).add(block);
                    }
                    return false;
                } else {
                    // block relocated - remove original block from currentAssignments
                    currentAssignments.remove(block);
                    return true;
                }
            }

            // attempt to relocate the blocked section somewhere else (shallow greedy)
            boolean attemptRelocateSection(Map<String, Object> blockedSection, Assignment originalBlock, int depth) {
                int secId = (Integer) blockedSection.get("id");
                int hours = Optional.ofNullable((Integer) blockedSection.get("course_hours")).orElse(3);

                // create session lengths for that section; we will try to place one session equal to original block length
                int len = originalBlock.timeslotIds.size();

                // candidate rooms and teachers for this section
                int prefType = blockedSection.get("preferred_room_type_id") == null ? -1 : (Integer) blockedSection.get("preferred_room_type_id");
                int courseSpec = blockedSection.get("course_spec") == null ? -1 : (Integer) blockedSection.get("course_spec");
                List<Map<String, Object>> rCandidates = roomsByType.getOrDefault(prefType, Collections.emptyList());
                if (rCandidates.isEmpty()) rCandidates = roomsByType.values().stream().flatMap(List::stream).collect(Collectors.toList());
                List<Map<String, Object>> tCandidates = teachersBySpec.getOrDefault(courseSpec, Collections.emptyList());
                if (tCandidates.isEmpty()) tCandidates = teachersBySpec.values().stream().flatMap(List::stream).collect(Collectors.toList());

                for (var entry : timeslotIdsByDay.entrySet()) {
                    String day = entry.getKey();
                    List<Integer> slotIds = entry.getValue();
                    for (int i = 0; i < slotIds.size(); i++) {
                        if (len == 2 && i + 1 >= slotIds.size()) continue;
                        List<Integer> candidate = (len == 1) ? List.of(slotIds.get(i)) : List.of(slotIds.get(i), slotIds.get(i+1));

                        for (Map<String, Object> room : rCandidates) {
                            int roomId = (Integer) room.get("id");
                            if (candidate.stream().anyMatch(ts -> roomOccupied.getOrDefault(ts, Set.of()).contains(roomId))) continue;
                            for (Map<String, Object> teacher : tCandidates) {
                                int tid = (Integer) teacher.get("id");
                                if (candidate.stream().anyMatch(ts -> teacherOccupied.getOrDefault(ts, Set.of()).contains(tid))) continue;
                                int curr = teacherDailyHours.getOrDefault(tid, new HashMap<>()).getOrDefault(day, 0);
                                if (curr + candidate.size() > 4) continue;

                                // assign relocated block
                                Assignment newBlock = new Assignment(secId, candidate, roomId, tid);
                                currentAssignments.add(newBlock);
                                for (int ts : candidate) {
                                    roomOccupied.computeIfAbsent(ts, k-> new HashSet<>()).add(roomId);
                                    teacherOccupied.computeIfAbsent(ts, k-> new HashSet<>()).add(tid);
                                    assignedByTimeslot.computeIfAbsent(ts, k-> new ArrayList<>()).add(newBlock);
                                }
                                teacherDailyHours.computeIfAbsent(tid, k-> new HashMap<>()).put(day, curr + candidate.size());
                                return true;
                            }
                        }
                    }
                }
                return false;
            }
        } // end RepairAttempt

        RepairAttempt ra = new RepairAttempt();

        // try all candidate slots day-by-day
        for (var entry : timeslotIdsByDay.entrySet()) {
            String day = entry.getKey();
            List<Integer> ids = entry.getValue();
            for (int i=0;i<ids.size();i++) {
                if (len==2 && i+1>=ids.size()) continue;
                List<Integer> candidate = len==1 ? List.of(ids.get(i)) : List.of(ids.get(i), ids.get(i+1));

                // quick feasibility: we will attempt to free candidate if blocked
                boolean ok = ra.attempt(candidate, 0);
                if (ok) return true;
            }
        }
        return false;
    }

    // helper small util to find day name for a timeslot id
    private String findDayForTimeslot(int timeslotId, Map<String, List<Integer>> timeslotIdsByDay) {
        for (var e : timeslotIdsByDay.entrySet()) {
            if (e.getValue().contains(timeslotId)) return e.getKey();
        }
        return "Unknown";
    }

    // helper to find section map by id - assumes 'sections' variable exists at scope of generate()
// We will add a private field to hold lastSections for lookup; modify generate() to set this before loop.
    private Map<Integer, Map<String, Object>> lastSectionsById = new HashMap<>();
    private Map<String, Object> findSectionById(int id) {
        return lastSectionsById.get(id);
    }


    private static class Assignment {
        int sectionId;
        List<Integer> timeslotIds;
        int roomId;
        int teacherId;
        Assignment(int s, List<Integer> ts, int r, int t) {
            this.sectionId = s; this.timeslotIds = ts; this.roomId = r; this.teacherId = t;
        }
    }

}
