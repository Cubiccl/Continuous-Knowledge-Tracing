package ckt;

import ckt.KTParameters.Gaussian;

public class Metric implements Comparable<Metric>
{

	/** The initial distribution before reducing & centering. */
	public Gaussian initialDistribution;
	/** This Metric's name. */
	public final String name;
	/** The threshold to determine if problems are valid for this Metric. */
	public double threshold;
	/** True if scores should be lower than the threshold to be valid, instead of higher by default. */
	public boolean thresholdReversed;
	/** The weight to aggregate. */
	public double weight;

	public Metric(String name)
	{
		this.name = name;
		this.initialDistribution = new Gaussian(0, 1);
		this.thresholdReversed = false;
	}

	@Override
	public int compareTo(Metric o)
	{
		return this.name.compareTo(o.name);
	}

}
