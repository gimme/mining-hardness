package dev.gimme.mininghardness;

public abstract class CommonConfig {

    public static CommonConfig INSTANCE;

    public abstract float getStartHardnessMultiplier();
    public abstract float getEndHardnessMultiplier();
    public abstract int getStartHardnessY();
    public abstract int getEndHardnessY();
    public abstract int getHardnessSoftCap();
    public abstract float getHardnessSoftCapMultiplier();
    public abstract float getToolDamageHardnessMultiplier();
    public abstract float getExhaustionHardnessMultiplier();
    public abstract boolean isHardnessInOverworldOnly();

    public float getHardnessMultiplier(int y) {
        float startMultiplier = getStartHardnessMultiplier();
        float endMultiplier = getEndHardnessMultiplier();
        int startY = getStartHardnessY();
        int endY = getEndHardnessY();

        if (y >= startY) {
            return startMultiplier;
        } else if (y <= endY) {
            return endMultiplier;
        } else {
            float factor = (float) (startY - y) / (startY - endY);
            return startMultiplier + factor * (endMultiplier - startMultiplier);
        }
    }

    public float getAdjustedHardness(float defaultHardness, int y) {
        if (defaultHardness == 0.0f) return 0.0f;

        float hardnessMultiplier = getHardnessMultiplier(y);
        float newHardness = defaultHardness * hardnessMultiplier;

        int hardnessSoftCap = getHardnessSoftCap();
        if (newHardness > hardnessSoftCap) {
            float softCapMultiplier = getHardnessSoftCapMultiplier();
            newHardness = hardnessSoftCap + (newHardness - hardnessSoftCap) * softCapMultiplier;
        }

        return newHardness;
    }

    public float getAdjustedExhaustion(float defaultExhaustion, int y) {
        float hardnessMultiplier = getHardnessMultiplier(y);
        float exhaustionHardnessMultiplier = getExhaustionHardnessMultiplier();
        return (defaultExhaustion * (1 + (hardnessMultiplier - 1) * exhaustionHardnessMultiplier));
    }

    public int getAdjustedToolDamage(float defaultDamage, int y) {
        float hardnessMultiplier = getHardnessMultiplier(y);
        float toolDamageHardnessMultiplier = getToolDamageHardnessMultiplier();
        return (int) Math.floor(defaultDamage * (1 + (hardnessMultiplier - 1) * toolDamageHardnessMultiplier));
    }
}
