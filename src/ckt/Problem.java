package ckt;

import java.util.HashMap;

import ckt.KTParameters.Gaussian;

public class Problem implements Comparable<Problem>
{

	/** The Knowledge aggregated from all metrics (only used for final Problems). */
	Gaussian aggregatedKnowledge;
	/** The knowledge expected after this Problem. */
	double expectedKnowledge;
	/** Index of the Problem in the Exploration. */
	int index;
	/** True if Problem is considered correct for the ideal sequence. */
	boolean isCorrect;
	/** The Knowledge computed after this Problem. */
	Gaussian knowledge;
	/** The knowledge for each individual metric. */
	public final HashMap<Metric, Gaussian> metricKnowledge;
	/** The score for each individual metric. */
	public final HashMap<Metric, Double> metricScores;
	/** Problem name. */
	public final String name;
	/** The focusness score of this problem. */
	double score;

	public Problem(String name)
	{
		this(name, -1);
	}

	public Problem(String name, int index)
	{
		this.name = name;
		this.index = index;
		this.metricScores = new HashMap<Metric, Double>();
		this.metricKnowledge = new HashMap<Metric, Gaussian>();
	}

	@Override
	public int compareTo(Problem o)
	{
		if (this.index == -1) return this.name.compareTo(o.name);
		return Integer.compare(this.index, o.index);
	}
}
