package dev.gimme.config;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.electronwill.nightconfig.toml.TomlFormat;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class ModConfigSpec {

    private CommentedFileConfig config;
    private final List<VariableBuilder> configValues = new ArrayList<>();

    private void onLoad() {
        configValues.forEach(value -> {
            if (!config.contains(value.key)) {
                if (value.comment != null) {
                    config.setComment(value.key, value.comment);
                }
                config.set(value.key, value.defaultValue.get());
                config.save();
            }
        });
    }

    public void init(@NotNull Path configDir, @NotNull String fileName) {
        config = CommentedFileConfig
            .builder(configDir.resolve(fileName), TomlFormat.instance())
            .onLoad(this::onLoad)
            .preserveInsertionOrder()
            .autoreload()
            .build();
        config.load();
    }

    public VariableBuilder variable() {
        return new VariableBuilder(this);
    }

    public static class VariableBuilder {

        private final ModConfigSpec spec;
        private @Nullable String comment;
        private String key;
        private Supplier<?> defaultValue;

        private VariableBuilder(ModConfigSpec spec) {
            this.spec = spec;
        }

        public VariableBuilder comment(String comment) {
            this.comment = comment;
            return this;
        }

        public <T> ConfigValue<T> define(@NotNull String key, T defaultValue) {
            return define(key, () -> defaultValue);
        }

        public <T> ConfigValue<T> define(@NotNull String key, @NotNull Supplier<T> defaultValue) {
            this.key = key;
            this.defaultValue = defaultValue;
            spec.configValues.add(this);
            return new ConfigValue<>(key, spec);
        }
    }

    public static class ConfigValue<T> {

        private final @NotNull String key;
        private final @NotNull ModConfigSpec spec;

        public ConfigValue(@NotNull String key, @NotNull ModConfigSpec spec) {
            this.key = key;
            this.spec = spec;
        }

        public T get() {
            if (spec.config == null) {
                throw new IllegalStateException("Config has not been initialized");
            }
            if (!spec.config.contains(key)) {
                throw new IllegalStateException("Config value " + key + " is not defined");
            }
            return spec.config.get(key);
        }
    }
}
