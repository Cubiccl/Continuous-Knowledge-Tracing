package ckt;

import java.util.Random;

/** The set of Parameters used in Knowledge Tracing. */
public class KTParameters
{
	/** Represents a Gaussian distribution by its mean and variation. */
	public static class Gaussian
	{

		/** The Gaussian mean. */
		public final double mean;
		/** The Gaussian variation. */
		public final double variation;

		public Gaussian()
		{
			this(0, 1);
		}

		public Gaussian(double mean)
		{
			this(mean, 1);
		}

		public Gaussian(double mean, double variation)
		{
			this.mean = mean;
			this.variation = variation;
		}

		/** @return A random value from this Gaussian distribution. */
		public double next()
		{
			double next = random.nextGaussian() * this.variation + this.mean;
			return next < 0 ? 0 : next > 1 ? 1 : next;
		}

		@Override
		public String toString()
		{
			return "(" + this.mean + ", " + this.variation + ")";
		}

		/** Apologies for the non-scientific method name.
		 * 
		 * @return The input value after reverting the center-reduce method. */
		public double unreduce(double value)
		{
			return value * this.variation + this.mean;
		}

	}

	/** Random number generator. */
	static final Random random = new Random();

	/** P(G) */
	public final Gaussian guess;
	/** P(S) */
	public final Gaussian slip;
	/** P(L0) */
	public final double startKnowledge;
	/** P(T) */
	public final double transition;

	public KTParameters(double startKnowledge, double transition, Gaussian guess, Gaussian slip)
	{
		super();
		this.startKnowledge = startKnowledge;
		this.transition = transition;
		this.guess = guess;
		this.slip = slip;
	}

}
