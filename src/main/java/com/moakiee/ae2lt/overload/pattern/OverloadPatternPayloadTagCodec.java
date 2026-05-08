package com.moakiee.ae2lt.overload.pattern;

import java.util.Objects;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

import com.moakiee.ae2lt.overload.model.EncodedOverloadPattern;
import com.moakiee.ae2lt.overload.model.MatchMode;
import com.moakiee.ae2lt.overload.model.OverloadPatternSlot;

/**
 * NBT bridge for overload-pattern item payloads.
 * <p>
 * This keeps item persistence concerns out of the model objects themselves.
 */
public final class OverloadPatternPayloadTagCodec {
    private static final String TAG_HOST_KIND = "HostKind";
    private static final String TAG_SOURCE_PATTERN = "SourcePattern";
    private static final String TAG_RULES = "Rules";
    private static final String TAG_INPUTS = "Inputs";
    private static final String TAG_OUTPUTS = "Outputs";
    private static final String TAG_SLOT = "Slot";
    private static final String TAG_MODE = "Mode";

    private OverloadPatternPayloadTagCodec() {
    }

    public static CompoundTag writePayload(OverloadPatternPayload payload) {
        Objects.requireNonNull(payload, "payload");

        var tag = new CompoundTag();
        tag.putString(TAG_HOST_KIND, payload.requiredHostKind().name());
        tag.put(TAG_SOURCE_PATTERN, payload.sourcePattern().toTag());
        tag.put(TAG_RULES, writeEncodedPattern(payload.encodedPattern()));
        return tag;
    }

    public static OverloadPatternPayload readPayload(CompoundTag tag) {
        Objects.requireNonNull(tag, "tag");

        var hostKind = PatternExecutionHostKind.valueOf(tag.getStringOr(
                TAG_HOST_KIND,
                PatternExecutionHostKind.OVERLOADED_PATTERN_PROVIDER.name()));
        var sourcePattern = SourcePatternSnapshot.fromTag(tag.getCompoundOrEmpty(TAG_SOURCE_PATTERN));
        var encodedPattern = readEncodedPattern(tag.getCompoundOrEmpty(TAG_RULES));
        return new OverloadPatternPayload(hostKind, sourcePattern, encodedPattern);
    }

    public static CompoundTag writeEncodedPattern(EncodedOverloadPattern encodedPattern) {
        Objects.requireNonNull(encodedPattern, "encodedPattern");

        var tag = new CompoundTag();
        tag.put(TAG_INPUTS, writeSlots(encodedPattern.inputSlots()));
        tag.put(TAG_OUTPUTS, writeSlots(encodedPattern.outputSlots()));
        return tag;
    }

    public static EncodedOverloadPattern readEncodedPattern(CompoundTag tag) {
        Objects.requireNonNull(tag, "tag");

        var builder = EncodedOverloadPattern.builder();

        var inputs = tag.getListOrEmpty(TAG_INPUTS);
        for (int i = 0; i < inputs.size(); i++) {
            var slotTag = inputs.getCompoundOrEmpty(i);
            builder.input(
                    slotTag.getIntOr(TAG_SLOT, 0),
                    MatchMode.valueOf(slotTag.getStringOr(TAG_MODE, MatchMode.STRICT.name())));
        }

        var outputs = tag.getListOrEmpty(TAG_OUTPUTS);
        for (int i = 0; i < outputs.size(); i++) {
            var slotTag = outputs.getCompoundOrEmpty(i);
            builder.output(
                    slotTag.getIntOr(TAG_SLOT, 0),
                    MatchMode.valueOf(slotTag.getStringOr(TAG_MODE, MatchMode.STRICT.name())));
        }

        return builder.build();
    }

    private static ListTag writeSlots(Iterable<OverloadPatternSlot> slots) {
        var list = new ListTag();
        for (var slot : slots) {
            var slotTag = new CompoundTag();
            slotTag.putInt(TAG_SLOT, slot.slotIndex());
            slotTag.putString(TAG_MODE, slot.matchMode().name());
            list.add(slotTag);
        }
        return list;
    }
}
