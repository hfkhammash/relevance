package org.jbpt.pm.relevance;

import org.deckfour.xes.model.XTrace;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class SimpleBackgroundModel implements ReplayInformationGatherer {

    public static double log2(double x) {
        return Math.log(x) / Math.log(2);
    }
    public static double h0(int accumulated_rho, double totalNumberOfTraces) {
        if (accumulated_rho == 0 || accumulated_rho == totalNumberOfTraces)
            return 0;
        else {
            double p = ((double) accumulated_rho) / totalNumberOfTraces;
            return -p * log2(p) - (1 - p) * log2(1 - p);
        }
    }


    int numberOfEvents = 0;
    int totalNumberOfTraces = 0;
    int totalNumberOfNonFittingTraces = 0;

    Set<String> labels = new HashSet<>();
    Map<String, Integer> traceFrequency = new HashMap<>();
    Map<String, Integer> traceSize = new HashMap<>();
    Map<String, Double> log2OfModelProbability = new HashMap<>();

    // Local values
    double lprob = 0.0;         // Trace replay probability
    String largeString = "";    // Identifier associated with the current trace (needed for identifying trace duplicates)

    // Methods to recollect statistics during log replay
    @Override
    public void openTrace(XTrace trace) {
        lprob = 0.0;
        largeString = "";
    }

    @Override
    public void closeTrace(XTrace trace, boolean fitting) {
        traceSize.put(largeString, trace.size());
        totalNumberOfTraces++;
        if (fitting)
            log2OfModelProbability.put(largeString, lprob / Math.log(2));
        else
            totalNumberOfNonFittingTraces++;
        traceFrequency.put(largeString, traceFrequency.getOrDefault(largeString, 0) + 1);
    }

    @Override
    public void processEvent(String eventLabel, double probability) {
        largeString += eventLabel;
        numberOfEvents++;
        labels.add(eventLabel);
        lprob += probability;
    }

    protected double costBitsUnfittingTraces(String traceId) {
        return (1 + traceSize.get(traceId)) * log2( 1 + labels.size() );
    }

    public Map<String, Object> computeRelevance(boolean full) {
        int accumulated_rho = 0;
        double accumulated_cost_bits = 0;
        double accumulated_temp_cost_bits = 0;
        double accumulated_prob_fitting_traces = 0;

        for (String traceString: traceFrequency.keySet()) {
            double traceFreq = traceFrequency.get(traceString);

            double cost_bits = 0.0;
            double nftrace_cost_bits = 0.0;

            if (log2OfModelProbability.containsKey(traceString)) { // fitting trace!
                cost_bits = -log2OfModelProbability.get(traceString);
                accumulated_rho += traceFreq;
            } else
                nftrace_cost_bits = cost_bits = costBitsUnfittingTraces(traceString);

            accumulated_temp_cost_bits += nftrace_cost_bits * traceFreq;

            accumulated_cost_bits += (cost_bits * traceFreq) / totalNumberOfTraces;

            if (log2OfModelProbability.containsKey(traceString))
                accumulated_prob_fitting_traces += traceFreq / totalNumberOfTraces;
        }

        if (full)
            return Map.of(
                    "numberOfTraces", totalNumberOfTraces,
                    "numberOfNonFittingTraces", totalNumberOfNonFittingTraces,
                    "coverage", accumulated_prob_fitting_traces,
                    "relevance", h0(accumulated_rho, totalNumberOfTraces) + accumulated_cost_bits,
                    "costOfBackgroundModel", accumulated_temp_cost_bits
            );
        else
            return Map.of("relevance", h0(accumulated_rho, totalNumberOfTraces) + accumulated_cost_bits);
    }
}