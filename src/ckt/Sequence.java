package ckt;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Random;

import ckt.KTParameters.Gaussian;

/** Represents a single Sequence. */
public class Sequence implements Comparable<Sequence>
{
	/** Number of random values generated to determine P(Ln). */
	static final int DRAWS = 30;

	/** Best similarity found for Knowledge sequences. */
	double bestSimilarity;
	/** The final Knowledge Sequence for this Sequence. */
	ArrayList<Double> knowledgeSequence;
	/** Sequence name. */
	public final String name;
	/** Probabilities for this Sequence. Some can be NaN if the sequence is full of 1 or full of 0. */
	KTParameters parameters;
	/** The list of Problems in this Sequence. */
	ArrayList<Problem> problems;

	public Sequence(String name)
	{
		this.name = name;
		this.problems = new ArrayList<Problem>();
	}

	@Override
	public int compareTo(Sequence o)
	{
		return this.name.toLowerCase().compareTo(o.name.toLowerCase());
	}

	/** Finds the Knowledge with P(Ln-1) = previous and Ci = score. */
	private Gaussian computeKnowledge(Gaussian previous, double score, KTParameters parameters)
	{
		double[] draws = new double[DRAWS];
		for (int i = 0; i < draws.length; ++i)
		{
			double slip = parameters.slip.next(), guess = parameters.guess.next(), knowledge = previous.next();
			double success = knowledge * (1 - slip) / (knowledge * (1 - slip) + (1 - knowledge) * guess);
			double failure = knowledge * slip / (knowledge * slip + (1 - knowledge) * (1 - guess));
			if (Double.isNaN(success)) success = 0;
			if (Double.isNaN(failure)) failure = 0;

			double weighted = success * score + failure * (1 - score);
			draws[i] = weighted + (1 - weighted) * parameters.transition;
		}

		return Utils.makeGaussian(draws);
	}

	/** Finds and sets the Knowledge after sequence n. */
	private Gaussian computeKnowledge(int n, KTParameters parameters, Metric metric)
	{
		if (n == 0)
		{
			Gaussian k = new Gaussian(parameters.startKnowledge, 0);
			if (metric == null) this.problems.get(0).knowledge = k;
			else this.problems.get(0).metricKnowledge.put(metric, k);
			return k;
		}

		Gaussian k = this.computeKnowledge(this.computeKnowledge(n - 1, parameters, metric),
				(metric == null ? this.problems.get(n).score : this.problems.get(n).metricScores.get(metric)), parameters);
		if (metric == null) this.problems.get(n).knowledge = k;
		else this.problems.get(n).metricKnowledge.put(metric, k);

		return this.problems.get(n).knowledge;
	}

	/** Determines the Knowledge values of this Sequence. */
	void computeKnowledge(KTParameters parameters, Metric metric)
	{
		this.computeKnowledge(this.problems.size() - 1, parameters, metric);
	}

	/** Determines P(T), P(G), P(S) */
	public void computeProbabilities(double startKnowledge)
	{
		double tNum = 0, tDenom = 0; // Numerator and denominator
		double gNum = 0, gDenom = 0;
		double sNum = 0, sDenom = 0;

		for (int i = 0; i < this.knowledgeSequence.size(); ++i)
		{
			if (i == 0) // K0 -> K1
			{
				tNum += (1 - startKnowledge) * this.knowledgeSequence.get(i);
				tDenom += 1 - startKnowledge;
			} else
			{
				tNum += (1 - this.knowledgeSequence.get(i - 1)) * this.knowledgeSequence.get(i);
				tDenom += 1 - this.knowledgeSequence.get(i - 1);
			}
			gNum += (this.problems.get(i).isCorrect ? 1 : 0) * (1 - this.knowledgeSequence.get(i));
			gDenom += (1 - this.knowledgeSequence.get(i));
			sNum += (this.problems.get(i).isCorrect ? 0 : 1) * this.knowledgeSequence.get(i);
			sDenom += this.knowledgeSequence.get(i);
		}

		this.parameters = new KTParameters(startKnowledge, tNum / tDenom, new Gaussian(gNum / gDenom), new Gaussian(sNum / sDenom));
	}

	/** @return The last Problem of this Sequence. */
	public Problem finalProblem()
	{
		return this.problems.get(this.problems.size() - 1);
	}

	/** Finds the best Knowledge Sequence for this Sequence. */
	void findKnowledgeSequence()
	{
		/* this.idealKnowledge = new ArrayList<Boolean>(); for (Problem problem : this.problems) this.idealKnowledge.add(problem.isFocused); */

		ArrayList<ArrayList<Boolean>> sequences = this.possibleKnowledgeSequences();
		sequences.sort(new Comparator<ArrayList<Boolean>>()
		{
			@Override
			public int compare(ArrayList<Boolean> o1, ArrayList<Boolean> o2)
			{
				// Revert as we want the highest first
				return -similarity(o1).compareTo(similarity(o2));
			}
		});

		this.bestSimilarity = -1;
		int total = 0;// Number of sequences with that similarity
		for (int i = 0; i < sequences.size(); ++i)
		{
			if (this.bestSimilarity == -1)
			{
				this.bestSimilarity = this.similarity(sequences.get(i));
				++total;
			} else if (this.similarity(sequences.get(i)) == this.bestSimilarity) ++total;
			else break;
		}

		while (sequences.size() > total)
			sequences.remove(total);

		this.knowledgeSequence = new ArrayList<Double>();
		double current;
		for (int i = 0; i < sequences.get(0).size(); ++i)
		{
			current = 0;
			for (ArrayList<Boolean> sequence : sequences)
				current += sequence.get(i) ? 1 : 0;
			current /= sequences.size();
			this.knowledgeSequence.add(current);
		}
	}

	/** Generates scores for this Sequence. Follows the formula and adds a bit of randomness. */
	@Deprecated
	public void generateScores(KTParameters parameters)
	{
		double current = parameters.startKnowledge, score;
		Random rand = new Random();
		for (int i = 0; i < this.problems.size(); ++i)
		{
			score = current * (1 - parameters.slip.next()) + (1 - current) * parameters.guess.next();
			score *= 0.4 * (rand.nextDouble() - 0.5) + 1;
			score = Math.min(1, Math.max(0, score));
			current = this.computeKnowledge(new Gaussian(current, 0), score, parameters).next();
			this.problems.get(i).score = score;
			this.problems.get(i).isCorrect = score > 0.5;
		}
	}

	/** @return All possible Knowledge Sequences for this Sequence. */
	ArrayList<ArrayList<Boolean>> possibleKnowledgeSequences()
	{
		ArrayList<ArrayList<Boolean>> sequences = new ArrayList<ArrayList<Boolean>>();

		for (int i = 0; i <= this.problems.size(); ++i) // n+1 possibilities (full 0 and full 1)
		{
			ArrayList<Boolean> sequence = new ArrayList<Boolean>();
			for (int j = 0; j < this.problems.size(); ++j)
				sequence.add(j >= i); // >= to get full 1
			sequences.add(sequence);
		}

		return sequences;
	}

	/** @return the similarity of the input <code>sequence</code> to this Sequence. */
	Double similarity(ArrayList<Boolean> sequence)
	{
		double similarity = 0;
		for (int i = 0; i < this.problems.size() && i < sequence.size(); ++i)
			if (this.problems.get(i).isCorrect == sequence.get(i)) ++similarity;
		return similarity / this.problems.size();
	}

}
