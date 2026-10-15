package adsa;

import models.TimetableSlot;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ADSA Module: Augmented Interval Tree for O(log n) Timetable Clash Detection.
 *
 * Mathematical Foundations:
 * - Each node stores an interval [low, high] representing [startMinutes, endMinutes).
 * - BST key: low (start time).
 * - Augmented attribute: maxHigh = max(high, left.maxHigh, right.maxHigh).
 * - Interval Overlap Condition:
 *     [low_1, high_1) overlaps [low_2, high_2) ⟺ low_1 < high_2 ∧ low_2 < high_1
 *
 * Query Complexity:
 * - O(log N) average query time to identify conflicts, compared to O(N) linear scan.
 */
public class IntervalTree {

    public static class Node {
        public int low;       // start time in minutes
        public int high;      // end time in minutes
        public int maxHigh;   // maximum endpoint in subtree
        public TimetableSlot slot;
        public Node left;
        public Node right;

        public Node(TimetableSlot slot) {
            this.slot = slot;
            this.low = slot.getStartMinutes();
            this.high = slot.getEndMinutes();
            this.maxHigh = this.high;
            this.left = null;
            this.right = null;
        }
    }

    // A separate Interval Tree for each day of the academic week
    private final Map<String, Node> dayRoots;

    public IntervalTree() {
        this.dayRoots = new HashMap<>();
    }

    public void clear() {
        dayRoots.clear();
    }

    /**
     * Inserts a slot into the appropriate day's interval tree.
     */
    public void insert(TimetableSlot slot) {
        String day = slot.getDayOfWeek().toUpperCase();
        Node root = dayRoots.get(day);
        dayRoots.put(day, insertNode(root, slot));
    }

    private Node insertNode(Node root, TimetableSlot slot) {
        if (root == null) {
            return new Node(slot);
        }

        int low = slot.getStartMinutes();
        if (low < root.low) {
            root.left = insertNode(root.left, slot);
        } else {
            root.right = insertNode(root.right, slot);
        }

        // Maintain augmented maxHigh invariant
        int leftMax = (root.left != null) ? root.left.maxHigh : 0;
        int rightMax = (root.right != null) ? root.right.maxHigh : 0;
        root.maxHigh = Math.max(root.high, Math.max(leftMax, rightMax));

        return root;
    }

    /**
     * Checks if a candidate slot overlaps with any currently scheduled slot.
     * Returns the conflicting slot if found, or null if no clash exists.
     */
    public TimetableSlot findClash(TimetableSlot candidate) {
        String day = candidate.getDayOfWeek().toUpperCase();
        Node root = dayRoots.get(day);
        if (root == null) {
            return null;
        }

        int qLow = candidate.getStartMinutes();
        int qHigh = candidate.getEndMinutes();
        return queryOverlap(root, qLow, qHigh);
    }

    private TimetableSlot queryOverlap(Node node, int qLow, int qHigh) {
        if (node == null) {
            return null;
        }

        // Check if current node's interval overlaps with [qLow, qHigh)
        // Interval overlap: qLow < node.high AND node.low < qHigh
        if (qLow < node.high && node.low < qHigh) {
            return node.slot;
        }

        // If left subtree exists and its maximum endpoint is strictly greater than qLow,
        // then an overlapping interval could be in the left subtree.
        if (node.left != null && node.left.maxHigh > qLow) {
            TimetableSlot leftRes = queryOverlap(node.left, qLow, qHigh);
            if (leftRes != null) return leftRes;
        }

        // Otherwise search right subtree
        return queryOverlap(node.right, qLow, qHigh);
    }

    /**
     * Finds all overlapping slots for the candidate slot.
     */
    public List<TimetableSlot> findAllClashes(TimetableSlot candidate) {
        List<TimetableSlot> clashes = new ArrayList<>();
        String day = candidate.getDayOfWeek().toUpperCase();
        Node root = dayRoots.get(day);
        if (root != null) {
            queryAllOverlaps(root, candidate.getStartMinutes(), candidate.getEndMinutes(), clashes);
        }
        return clashes;
    }

    private void queryAllOverlaps(Node node, int qLow, int qHigh, List<TimetableSlot> results) {
        if (node == null) return;

        if (qLow < node.high && node.low < qHigh) {
            results.add(node.slot);
        }

        if (node.left != null && node.left.maxHigh > qLow) {
            queryAllOverlaps(node.left, qLow, qHigh, results);
        }

        if (node.right != null && node.low < qHigh) {
            queryAllOverlaps(node.right, qLow, qHigh, results);
        }
    }

    /**
     * Data structure holding formatted clash details for viva demonstration.
     */
    public static class ClashDetail {
        private final boolean clashDetected;
        private final TimetableSlot existingSlot;
        private final TimetableSlot candidateSlot;
        private final String overlapWindow;
        private final String formattedExplanation;

        public ClashDetail(boolean clashDetected, TimetableSlot existingSlot, TimetableSlot candidateSlot, String overlapWindow, String formattedExplanation) {
            this.clashDetected = clashDetected;
            this.existingSlot = existingSlot;
            this.candidateSlot = candidateSlot;
            this.overlapWindow = overlapWindow;
            this.formattedExplanation = formattedExplanation;
        }

        public boolean isClashDetected() {
            return clashDetected;
        }

        public TimetableSlot getExistingSlot() {
            return existingSlot;
        }

        public TimetableSlot getCandidateSlot() {
            return candidateSlot;
        }

        public String getOverlapWindow() {
            return overlapWindow;
        }

        public String getFormattedExplanation() {
            return formattedExplanation;
        }
    }

    public static String minutesToTimeString(int totalMinutes) {
        int hours = totalMinutes / 60;
        int mins = totalMinutes % 60;
        return String.format("%02d:%02d", hours, mins);
    }

    /**
     * Inspects a candidate slot against the interval tree and returns viva-ready explanation.
     */
    public ClashDetail inspectClash(TimetableSlot candidate) {
        TimetableSlot conflict = findClash(candidate);
        if (conflict == null) {
            return new ClashDetail(false, null, candidate, null, "✓ No timetable clash detected");
        }

        int overlapStart = Math.max(candidate.getStartMinutes(), conflict.getStartMinutes());
        int overlapEnd = Math.min(candidate.getEndMinutes(), conflict.getEndMinutes());
        String window = minutesToTimeString(overlapStart) + " - " + minutesToTimeString(overlapEnd);

        String existingCourse = conflict.getCourseCode() != null ? conflict.getCourseCode() : "Course " + conflict.getCourseId();
        String candidateCourse = candidate.getCourseCode() != null ? candidate.getCourseCode() : "Course " + candidate.getCourseId();

        String explanation = String.format(
                "⚠ TIMETABLE CLASH DETECTED%n%nExisting:%n%s%n%s %s - %s (Room %s)%n%nNew:%n%s%n%s %s - %s (Room %s)%n%nConflict Interval: %s%nRegistration blocked.",
                existingCourse, conflict.getDayOfWeek(), conflict.getStartTime(), conflict.getEndTime(), conflict.getRoomNumber(),
                candidateCourse, candidate.getDayOfWeek(), candidate.getStartTime(), candidate.getEndTime(), candidate.getRoomNumber(),
                window
        );

        return new ClashDetail(true, conflict, candidate, window, explanation);
    }

    /**
     * Bulk population from a student's active schedule.
     */
    public void populate(List<TimetableSlot> slots) {
        clear();
        for (TimetableSlot s : slots) {
            insert(s);
        }
    }
}
