package ckt;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;

/** Splits an Exploration. If the Exploration contains two or more wrong-correct sequences, the Exploration will be split into multiple ones. */
public class ExplorationSplitter
{

	@Deprecated
	public static void findArrays()
	{
		for (Sequence sequence : Main.allSequences)
		{
			ArrayList<Integer> array = new ArrayList<Integer>();
			int current = 0, count = 0;
			for (Problem problem : sequence.problems)
			{
				if ((current == 1) != problem.isCorrect)
				{
					current = current == 0 ? 1 : 0;
					array.add(count);
					count = 0;
				}
				++count;
			}
			array.add(count);
		}
	}

	/** @param sequence - The Sequence to split.
	 * @param indexes - The index of the first Problem of each new Sequence. */
	@Deprecated
	public static void splitSequence(Sequence sequence, int... indexes)
	{
		Main.allSequences.remove(sequence);
		if (indexes.length == 0) return;
		ArrayList<Problem> problems = new ArrayList<Problem>();
		int current = 1;
		for (int i = 0; i < sequence.problems.size(); ++i)
		{
			if (current < indexes.length && indexes[current] == i)
			{
				Sequence e = new Sequence(sequence.name + "-" + Integer.toString(current));
				e.problems.addAll(problems);
				Main.allSequences.add(e);
				problems.clear();
				++current;
			}
			problems.add(sequence.problems.get(i));
		}
		Sequence e = new Sequence(sequence.name + "-" + Integer.toString(current));
		e.problems.addAll(problems);
		Main.allSequences.add(e);
		problems.clear();
	}

	/** Splits the sequences in {@link Main#allSequences}. */
	public static void splitSequences(int maxNoise)
	{
		HashSet<Sequence> split = new HashSet<Sequence>(); // To avoid ConcurrentModificationException
		for (Sequence sequence : Main.allSequences)
		{
			ExplorationSplitter splitter = new ExplorationSplitter(sequence, maxNoise);
			splitter.doSplit();
			split.addAll(splitter.split);
		}

		Main.allSequences.clear();
		Main.allSequences.addAll(split);
		Main.cleanSequences(false, false);
	}

	/** Maximum length of a zone to be potentially considered as noise. */
	public final int maxNoise;
	/** The sequence to split. */
	public final Sequence sequence;
	/** The sequence after splitting. */
	public final ArrayList<Sequence> split;
	/** The sequence as zones. */
	private final ArrayList<Zone> zones;

	public ExplorationSplitter(Sequence sequence, int maxNoise)
	{
		this.sequence = sequence;
		this.maxNoise = maxNoise;
		this.split = new ArrayList<Sequence>();
		this.zones = new ArrayList<Zone>();
	}

	/** Creates the new split sequences. */
	private void createNewSequences()
	{
		int index = 0;
		for (int zone = 0; zone < this.zones.size(); ++zone)
		{
			++index;
			Sequence newSequence = new Sequence(this.sequence.name + "-" + Integer.toString(index));
			newSequence.problems.addAll(this.zones.get(zone).getProblems());

			if (zone != 0 || !this.zones.get(zone).majority())
			{
				++zone;
				if (zone < this.zones.size()) newSequence.problems.addAll(this.zones.get(zone).getProblems());
			}

			this.split.add(newSequence);
		}
	}

	/** Executes the splitting. */
	public void doSplit()
	{
		this.init();
		byte split = Zone.MERGE_NEXT;
		ArrayList<Zone> available = new ArrayList<Zone>();

		// First step: Eliminate noise
		while (split != Zone.NO_MERGE)
		{
			available.clear();
			available.addAll(this.zones);
			available.sort(new Comparator<Zone>()
			{
				// To select smaller zones first.
				@Override
				public int compare(Zone o1, Zone o2)
				{
					return Integer.compare(o1.size(), o2.size());
				}
			});

			for (Zone current : available)
			{
				int index = this.zones.indexOf(current);
				Zone previous = index == 0 ? null : this.zones.get(index - 1);
				Zone next = index == this.zones.size() - 1 ? null : this.zones.get(index + 1);
				split = current.mergeIfNoise(previous, next);
				if (split == Zone.MERGE_PREVIOUS)
				{
					this.zones.remove(previous);
					break;
				}
				if (split == Zone.MERGE_NEXT)
				{
					this.zones.remove(next);
					break;
				}
			}
		}

		split = Zone.MERGE_NEXT;
		// Second step: Merge zones
		while (split != Zone.NO_MERGE)
		{
			available.clear();
			available.addAll(this.zones);
			available.sort(new Comparator<Zone>()
			{
				// To select smaller zones first.
				@Override
				public int compare(Zone o1, Zone o2)
				{
					int size = Integer.compare(o1.size(), o2.size());
					if (size == 0)
					{
						int min = Integer.compare(o1.minExtremity(), o2.minExtremity());
						if (min == 0) return Integer.compare(o1.startIndex, o2.startIndex);
						return min;
					}
					return size;
				}
			});

			for (Zone current : available)
			{
				int index = this.zones.indexOf(current);
				Zone previous = index == 0 ? null : this.zones.get(index - 1);
				Zone next = index == this.zones.size() - 1 ? null : this.zones.get(index + 1);
				split = current.mergeWithNeighbour(previous, next);
				if (split == Zone.MERGE_PREVIOUS)
				{
					this.zones.remove(previous);
					break;
				}
				if (split == Zone.MERGE_NEXT)
				{
					this.zones.remove(next);
					break;
				}
			}
		}

		if (this.zones.size() > 2) this.createNewSequences();
		else this.split.add(this.sequence);
	}

	/** Creates the smallest zones possible. */
	private void init()
	{
		int start = 0, current = 0;
		for (int problem = 0; problem <= this.sequence.problems.size(); ++problem)
		{
			if (problem == this.sequence.problems.size() || (current == 1) != this.sequence.problems.get(problem).isCorrect)
			{
				current = current == 0 ? 1 : 0;
				this.zones.add(new Zone(this.sequence, this.maxNoise, start, problem == 0 ? 0 : problem - 1));
				start = problem;
			}
		}
	}

}
