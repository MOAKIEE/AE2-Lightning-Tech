package com.moakiee.ae2lt.overload.provider;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Objects;

import appeng.api.networking.security.IActionSource;

import com.moakiee.ae2lt.overload.pattern.OverloadPatternDetails;

/**
 * Resolves overload-pattern input slots against network inventory.
 * <p>
 * Key behaviors:
 * <ul>
 *   <li>STRICT slots only match one exact variant at a time</li>
 *   <li>ID_ONLY slots aggregate all exact variants with the same item id</li>
 *   <li>exact extraction slices are planned in a stable order</li>
 *   <li>the concrete extraction ledger is preserved for later retry/return</li>
 * </ul>
 * This class does not perform machine submission and does not decide crafting
 * completion. It only prepares provider-side input material.
 */
public final class OverloadIngredientResolver {
    public ExtractionPlan plan(OverloadPatternDetails patternDetails, int copies, OverloadInventoryView inventoryView) {
        Objects.requireNonNull(patternDetails, "patternDetails");
        Objects.requireNonNull(inventoryView, "inventoryView");
        if (copies <= 0) {
            throw new IllegalArgumentException("copies must be > 0");
        }

        var slotPlans = new ArrayList<PlannedInputSlot>();
        for (var input : patternDetails.inputs()) {
            var request = OverloadIngredientRequest.fromInput(input, copies);
            slotPlans.add(planSlot(request, inventoryView));
        }
        return new ExtractionPlan(copies, slotPlans);
    }

    public OverloadDispatchPayload extract(
            ExtractionPlan plan,
            OverloadInventoryView inventoryView,
            IActionSource source
    ) {
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(inventoryView, "inventoryView");
        Objects.requireNonNull(source, "source");

        var payloadSlots = new ArrayList<PayloadInputSlot>();
        for (var slotPlan : plan.slots()) {
            var extracted = new ArrayList<ExtractedPayloadStack>();
            for (var extraction : slotPlan.extractions()) {
                var result = inventoryView.extract(extraction.storageKey(), extraction.amount(), source);
                if (!result.isEmpty()) {
                    extracted.add(new ExtractedPayloadStack(
                            slotPlan.slotIndex(),
                            result.storageKey(),
                            result.stack(),
                            result.extractedAmount()));
                }
            }
            payloadSlots.add(new PayloadInputSlot(
                    slotPlan.slotIndex(),
                    slotPlan.template(),
                    slotPlan.requiredAmount(),
                    slotPlan.matchMode(),
                    extracted));
        }
        return new OverloadDispatchPayload(plan.copies(), payloadSlots);
    }

    private PlannedInputSlot planSlot(OverloadIngredientRequest request, OverloadInventoryView inventoryView) {
        var matched = inventoryView.findCandidates(request.compareKey()).stream()
                .filter(candidate -> request.compareKey().matches(candidate.stack()))
                .sorted(stableCandidateOrder())
                .toList();

        long remaining = request.requiredAmount();
        var extractions = new ArrayList<PlannedExtraction>();

        for (var candidate : matched) {
            if (remaining <= 0) {
                break;
            }

            long toTake = Math.min(candidate.availableAmount(), remaining);
            if (toTake > 0) {
                extractions.add(new PlannedExtraction(
                        request.slotIndex(),
                        candidate.storageKey(),
                        candidate.stack(),
                        toTake));
                remaining -= toTake;
            }
        }

        return new PlannedInputSlot(
                request.slotIndex(),
                request.template(),
                request.requiredAmount(),
                request.matchMode(),
                request.compareKey(),
                extractions);
    }

    private static Comparator<OverloadInventoryCandidate> stableCandidateOrder() {
        return OverloadInventoryCandidate.STABLE_ORDER;
    }

    /*
     * Future provider usage sketch:
     *
     * SINGLE_TARGET:
     *   plan = resolver.plan(overloadPattern, 1, inventoryView);
     *   if (!plan.isComplete()) return false;
     *   payload = resolver.extract(plan, inventoryView, source);
     *   machineAdapter.pushResolvedPayload(..., payload, ...);
     *
     * EVEN_DISTRIBUTION:
     *   for each candidate target in least-loaded order:
     *       plan = resolver.plan(overloadPattern, 1, inventoryView);
     *       if (!plan.isComplete()) return false;
     *       payload = resolver.extract(plan, inventoryView, source);
     *       if (target accepts payload) return true;
     *       return extracted payload back to network and try next target
     *
     * Note: capacity prediction stays out of scope. A target may accept only
     * part of the pushed payload; the unaccepted remainder stays with provider
     * retry/overflow handling and is not "completed" here.
     */
}
