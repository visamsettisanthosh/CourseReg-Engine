package adsa;

import java.util.*;

/**
 * ADSA + DMGT Module: Prerequisite Directed Acyclic Graph (DAG).
 *
 * Mathematical Properties (DMGT):
 * - A prerequisite relationship forms a strict partial order relation R on courses:
 *     Irreflexive: (c, c) ∉ R
 *     Asymmetric:  (c1, c2) ∈ R ⟹ (c2, c1) ∉ R
 *     Transitive:  (c1, c2) ∈ R ∧ (c2, c3) ∈ R ⟹ (c1, c3) ∈ R
 * - The graph G = (V, E) where E = {(c, p) | c requires p} must be acyclic.
 * - By the Fundamental Theorem of DAGs, G has at least one topological ordering
 *   if and only if G contains no directed cycles.
 */
public class PrerequisiteDAG {
    // Adjacency List: courseId -> List of immediate prerequisite courseIds
    private final Map<Integer, List<Integer>> prereqAdjacencyList;
    // Reverse Adjacency List: prereqId -> List of courses that depend on this prereq
    private final Map<Integer, List<Integer>> dependentAdjacencyList;
    // Map courseId -> Course Code/Name for clear error messaging
    private final Map<Integer, String> courseNames;

    public PrerequisiteDAG() {
        this.prereqAdjacencyList = new HashMap<>();
        this.dependentAdjacencyList = new HashMap<>();
        this.courseNames = new HashMap<>();
    }

    public void registerCourse(int courseId, String courseName) {
        prereqAdjacencyList.putIfAbsent(courseId, new ArrayList<>());
        dependentAdjacencyList.putIfAbsent(courseId, new ArrayList<>());
        courseNames.putIfAbsent(courseId, courseName);
    }

    /**
     * Adds a directed dependency: courseId requires prereqCourseId.
     * Validates that this edge does not introduce a cycle.
     */
    public boolean addPrerequisiteEdge(int courseId, int prereqCourseId) {
        registerCourse(courseId, "Course " + courseId);
        registerCourse(prereqCourseId, "Course " + prereqCourseId);

        // Self-loop prevention
        if (courseId == prereqCourseId) {
            throw new IllegalArgumentException("Course cannot be a prerequisite of itself: " + courseId);
        }

        // Tentatively add edge
        prereqAdjacencyList.get(courseId).add(prereqCourseId);
        dependentAdjacencyList.get(prereqCourseId).add(courseId);

        // Enforce DAG invariant: check if cycle is created
        if (hasCycle()) {
            // Rollback edge
            prereqAdjacencyList.get(courseId).remove((Integer) prereqCourseId);
            dependentAdjacencyList.get(prereqCourseId).remove((Integer) courseId);
            throw new IllegalStateException("Adding prerequisite edge " + courseId + " -> " + prereqCourseId + " violates DAG property (creates a cycle)!");
        }

        return true;
    }

    /**
     * ADSA Cycle Detection using 3-Color DFS:
     * WHITE (0) = Unvisited
     * GRAY  (1) = Currently in recursion stack (visiting ancestors)
     * BLACK (2) = Completely processed
     *
     * If a neighbor is GRAY, a back-edge exists, proving a cycle exists.
     */
    public boolean hasCycle() {
        return detectCycleDetails() != null;
    }

    /**
     * Detects cycles and returns an informative diagnostic message or null if acyclic.
     */
    public String detectCycleDetails() {
        Map<Integer, Integer> state = new HashMap<>();
        Map<Integer, Integer> parent = new HashMap<>();
        for (int courseId : prereqAdjacencyList.keySet()) {
            state.put(courseId, 0); // 0 = WHITE
        }

        for (int courseId : prereqAdjacencyList.keySet()) {
            if (state.get(courseId) == 0) {
                List<Integer> cyclePath = new ArrayList<>();
                if (dfsFindCyclePath(courseId, state, parent, cyclePath)) {
                    StringBuilder sb = new StringBuilder("❌ Invalid prerequisite graph\nCycle detected: ");
                    for (int i = 0; i < cyclePath.size(); i++) {
                        sb.append(getCourseName(cyclePath.get(i)));
                        if (i < cyclePath.size() - 1) sb.append(" → ");
                    }
                    return sb.toString();
                }
            }
        }
        return null; // Acyclic graph
    }

    private boolean dfsFindCyclePath(int u, Map<Integer, Integer> state, Map<Integer, Integer> parent, List<Integer> cyclePath) {
        state.put(u, 1); // 1 = GRAY (visiting)

        List<Integer> prereqs = prereqAdjacencyList.getOrDefault(u, Collections.emptyList());
        for (int v : prereqs) {
            int vState = state.getOrDefault(v, 0);
            if (vState == 1) {
                // Back-edge found: reconstruct cycle path
                cyclePath.add(u);
                cyclePath.add(v);
                return true;
            }
            if (vState == 0) {
                parent.put(v, u);
                if (dfsFindCyclePath(v, state, parent, cyclePath)) {
                    if (!cyclePath.contains(u)) {
                        cyclePath.add(0, u);
                    }
                    return true;
                }
            }
        }

        state.put(u, 2); // 2 = BLACK (visited)
        return false;
    }

    /**
     * Computes eligibility status for a course node relative to an active student.
     * Returns: COMPLETED, ENROLLED, ELIGIBLE, or PREREQ REQUIRED.
     */
    public String getNodeStatusForStudent(int courseId, Set<Integer> completedCourses, Set<Integer> enrolledCourses) {
        if (completedCourses.contains(courseId)) {
            return "COMPLETED";
        }
        if (enrolledCourses.contains(courseId)) {
            return "ENROLLED";
        }
        List<Integer> missing = findMissingPrerequisites(courseId, completedCourses);
        if (missing.isEmpty()) {
            return "ELIGIBLE";
        } else {
            return "PREREQ REQUIRED";
        }
    }

    /**
     * ADSA Topological Sort using Kahn's In-Degree Reduction Algorithm.
     * Computes valid study sequence starting from foundational courses.
     */
    public List<Integer> getTopologicalSort() {
        Map<Integer, Integer> inDegree = new HashMap<>();
        for (int courseId : prereqAdjacencyList.keySet()) {
            inDegree.put(courseId, 0);
        }

        // Note: in dependency graph, courses with NO prerequisites have in-degree 0
        for (int courseId : prereqAdjacencyList.keySet()) {
            inDegree.put(courseId, prereqAdjacencyList.get(courseId).size());
        }

        Queue<Integer> queue = new LinkedList<>();
        for (Map.Entry<Integer, Integer> entry : inDegree.entrySet()) {
            if (entry.getValue() == 0) {
                queue.add(entry.getKey());
            }
        }

        List<Integer> topoOrder = new ArrayList<>();
        while (!queue.isEmpty()) {
            int curr = queue.poll();
            topoOrder.add(curr);

            // For all courses that depend on curr, reduce their needed prereq count
            List<Integer> dependents = dependentAdjacencyList.getOrDefault(curr, Collections.emptyList());
            for (int dep : dependents) {
                inDegree.put(dep, inDegree.get(dep) - 1);
                if (inDegree.get(dep) == 0) {
                    queue.add(dep);
                }
            }
        }

        if (topoOrder.size() != prereqAdjacencyList.size()) {
            throw new IllegalStateException("Graph contains cycles; topological sort is undefined!");
        }

        return topoOrder;
    }

    /**
     * Retrieves immediate prerequisites for a given course.
     */
    public List<Integer> getDirectPrerequisites(int courseId) {
        return prereqAdjacencyList.getOrDefault(courseId, Collections.emptyList());
    }

    /**
     * Validates whether a student has completed all prerequisites for targetCourseId.
     * Returns a list of missing prerequisite IDs (empty list if all met).
     */
    public List<Integer> findMissingPrerequisites(int targetCourseId, Set<Integer> completedCourseIds) {
        List<Integer> directPrereqs = getDirectPrerequisites(targetCourseId);
        List<Integer> missing = new ArrayList<>();

        for (int prereqId : directPrereqs) {
            if (!completedCourseIds.contains(prereqId)) {
                missing.add(prereqId);
            }
        }
        return missing;
    }

    /**
     * Computes full transitive prerequisite chain for targetCourseId using BFS.
     */
    public Set<Integer> getAllTransitivePrerequisites(int targetCourseId) {
        Set<Integer> allPrereqs = new HashSet<>();
        Queue<Integer> queue = new LinkedList<>();
        queue.addAll(getDirectPrerequisites(targetCourseId));

        while (!queue.isEmpty()) {
            int curr = queue.poll();
            if (allPrereqs.add(curr)) {
                queue.addAll(getDirectPrerequisites(curr));
            }
        }
        return allPrereqs;
    }

    public String getCourseName(int courseId) {
        return courseNames.getOrDefault(courseId, "Course " + courseId);
    }
}
