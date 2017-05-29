package ckt;

import ckt.KTParameters.Gaussian;

public class Metric implements Comparable<Metric>
{

	/** The initial distribution before reducing & centering. */
	public Gaussian initialDistribution;
	/** This Metric's name. */
	public final String name;
	/** The parameters found for each step in cross validation, for each skill. <br />
	 * Each has a size of {@link Main#validations} + 2. First <code>validations</code> values are the parameters. <br />
	 * Index <code>validations</code> has means for these parameters, <code>validations+1</code> has variations. */
	public KTParameters[] parameters;
	/** The weight to aggregate. */
	public double weight;

	public Metric(String name)
	{
		this.name = name;
		this.initialDistribution = new Gaussian(0, 1);
	}

	@Override
	public int compareTo(Metric o)
	{
		return this.name.compareTo(o.name);
	}

}
