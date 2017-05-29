package ckt;

import java.util.ArrayList;

/** Represents a zone in a sequence. Used to split explorations in {@link ExplorationSplitter}. */
public class Zone implements Comparable<Zone>
{
	public static final byte NO_MERGE = 0, MERGE_PREVIOUS = 1, MERGE_NEXT = 2;

	/** @return The number of consecutive equal values at the end of <code>zone</code>. */
	private static int endOf(Zone zone)
	{
		int count = 1;
		boolean value = zone.sequence.problems.get(zone.endIndex).isCorrect;
		for (int problem = zone.endIndex - 1; problem >= zone.startIndex; --problem)
			if (value != zone.sequence.problems.get(problem).isCorrect) break; // If the value is different, stop
			else ++count; // Else, found another one ! Increment.

		return count;
	}

	/** @return The number of consecutive equal values at the start of <code>zone</code>. */
	private static int startOf(Zone zone)
	{
		int count = 1;
		boolean value = zone.sequence.problems.get(zone.startIndex).isCorrect;
		for (int problem = zone.startIndex + 1; problem <= zone.endIndex; ++problem)
			if (value != zone.sequence.problems.get(problem).isCorrect) break; // If the value is different, stop
			else ++count; // Else, found another one ! Increment.

		return count;
	}

	/** Maximum length to be potentially considered as noise. */
	public final int maxNoise;
	/** The Sequence this zone describes. */
	public final Sequence sequence;
	/** Start and end indexes of the zone. */
	public int startIndex, endIndex;

	public Zone(Sequence sequence, int maxNoise)
	{
		this(sequence, maxNoise, 0, 0);
	}

	public Zone(Sequence sequence, int maxNoise, int startIndex, int endIndex)
	{
		this.sequence = sequence;
		this.maxNoise = maxNoise;
		this.startIndex = startIndex;
		this.endIndex = endIndex;
	}

	/** Determines if this zone can merge with the previous or the next.
	 * 
	 * @return {@link Zone#NO_MERGE} if can't be merged.<br />
	 *         {@link Zone#MERGE_PREVIOUS} or {@link Zone#MERGE_NEXT} if it can merge respectively with the previous or next zone. */
	private byte canMerge(Zone previous, Zone next)
	{
		int start = startOf(this), end = endOf(this);
		boolean majority = this.majority();
		if (start >= this.maxNoise && end >= this.maxNoise) return NO_MERGE;

		if (start <= end) // Compare with smallest first
		{
			if (previous != null && start < previous.size() && majority == previous.majority()) return MERGE_PREVIOUS;
			if (next != null && end < next.size() && majority == next.majority()) return MERGE_NEXT;
		} else
		{
			if (next != null && end < next.size() && majority == next.majority()) return MERGE_NEXT;
			if (previous != null && start < previous.size() && majority == previous.majority()) return MERGE_PREVIOUS;
		}

		return NO_MERGE;
	}

	@Override
	public int compareTo(Zone o)
	{
		return Integer.compare(this.startIndex, o.startIndex);
	}

	/** @return The Problems contained in this Zone. */
	public ArrayList<Problem> getProblems()
	{
		ArrayList<Problem> problems = new ArrayList<Problem>();
		for (int problem = this.startIndex; problem <= this.endIndex; ++problem)
			problems.add(this.sequence.problems.get(problem));
		return problems;
	}

	/** @return <code>NO_MERGE</code> if no merge is possible.<br />
	 *         <code>MERGE_PREVIOUS</code> if should merge with previous zone.<br />
	 *         <code>MERGE_NEXT</code> if should merge with next zone. */
	public byte isNoise(Zone previous, Zone next)
	{
		if (this.size() >= this.maxNoise) return NO_MERGE;
		if (this.majority()) // If majority is focused, compare with previous first
		{
			if (previous != null && this.size() < previous.size()) return MERGE_PREVIOUS;
			if (next != null && this.size() < next.size()) return MERGE_NEXT;
		} else
		// Else compare with next first
		{
			if (next != null && this.size() < next.size()) return MERGE_NEXT;
			if (previous != null && this.size() < previous.size()) return MERGE_PREVIOUS;
		}
		return NO_MERGE;
	}

	/** @return <code>true</code> if the majority of problems in this zone are correct. */
	public boolean majority()
	{
		int focus = 0, not = 1;
		for (int problem = this.startIndex; problem <= this.endIndex; ++problem)
			if (this.sequence.problems.get(problem).isCorrect) ++focus;
			else ++not;
		return focus >= not;
	}

	/** @return {@link Zone#MERGE_PREVIOUS} or {@link Zone#MERGE_NEXT} if this FocusZone is considered as noise. If so, it will merge with the best neighbour. <br />
	 *         {@link Zone#NO_MERGE} else. */
	public byte mergeIfNoise(Zone previous, Zone next)
	{
		byte merge = this.isNoise(previous, next);
		if (merge == MERGE_PREVIOUS) this.mergePrevious(previous);
		else if (merge == MERGE_NEXT) this.mergeNext(next);
		return merge;
	}

	/** Merges this zone with the <code>next</code>. */
	public void mergeNext(Zone next)
	{
		this.endIndex = next.endIndex;
	}

	/** Merges this zone with the <code>previous</code>. */
	public void mergePrevious(Zone previous)
	{
		this.startIndex = previous.startIndex;
	}

	/** Merges with a neighbor if possible.
	 * 
	 * @return NO_MERGE if there was no merge. MERGE_PREVIOUS or MERGE_NEXT else. */
	public byte mergeWithNeighbour(Zone previous, Zone next)
	{
		byte merge = this.canMerge(previous, next);
		if (merge == MERGE_PREVIOUS) this.mergePrevious(previous);
		else if (merge == MERGE_NEXT) this.mergeNext(next);
		return merge;
	}

	/** @return The minimum number of consecutive identical values at the start or end of this zone. */
	public int minExtremity()
	{
		int start = startOf(this), end = endOf(this);
		return start < end ? start : end;
	}

	/** @return The size of this zone. */
	public int size()
	{
		return this.endIndex - this.startIndex + 1;
	}

	@Override
	public String toString()
	{
		String s = "";
		for (Problem q : this.getProblems())
			s += q.isCorrect ? 1 : 0;
		return s;
	}
}
