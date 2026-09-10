package com.fish.mirebound.mud.tuning;

import com.fish.mirebound.mud.MudBlockProfileStore;
import com.fish.mirebound.mud.MudPhysicsParameter;
import java.util.Arrays;

/** Scan-local aggregation of immutable profiles without a parameter-vector copy per block. */
final class MudTuningProfileSummary {
    private static final MudPhysicsParameter[] PARAMETERS = MudPhysicsParameter.values();
    private final boolean[] mixed = new boolean[MudPhysicsParameter.COUNT];
    private double[] firstValues;
    private Object lastProfile;

    void offer(double[] baseline, MudBlockProfileStore.Profile local) {
        Object identity = local == null ? baseline : local;
        if (identity == lastProfile) {
            return;
        }
        if (firstValues == null) {
            firstValues = local == null ? baseline.clone() : local.values();
        } else {
            for (MudPhysicsParameter parameter : PARAMETERS) {
                int index = parameter.ordinal();
                if (!mixed[index]) {
                    double offered = local == null ? baseline[index] : local.value(parameter);
                    mixed[index] = !parameter.displayEquivalent(firstValues[index], offered);
                }
            }
        }
        lastProfile = identity;
    }

    double[] displayed(double[] baseline) {
        double[] result = Arrays.copyOf(firstValues, firstValues.length);
        for (int index = 0; index < mixed.length; index++) {
            if (mixed[index]) {
                result[index] = baseline[index];
            }
        }
        return result;
    }
}
