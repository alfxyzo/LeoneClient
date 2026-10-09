package dev.alfxyz.leoneclient.module;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

/** A module setting, shown in a group card on the module's settings page. */
public abstract sealed class Setting permits Setting.Toggle, Setting.Slider, Setting.Chips, Setting.Choice, Setting.Text {
	public final String id;
	public final String name;
	public final String group;
	/** One or two sentences shown when hovering the setting. */
	public String description = "";
	private BooleanSupplier shown = () -> true;

	protected Setting(String id, String name, String group) {
		this.id = id;
		this.name = name;
		this.group = group;
	}

	/** Only show this setting while {@code condition} holds. */
	public Setting shownWhen(BooleanSupplier condition) {
		this.shown = condition;
		return this;
	}

	public boolean shown() {
		return shown.getAsBoolean();
	}

	public abstract JsonElement save();

	public abstract void load(JsonElement json);

	/** Restores the default value. */
	public abstract void reset();

	public static final class Toggle extends Setting {
		public final boolean defaultValue;
		public boolean value;

		public Toggle(String id, String name, String group, boolean value) {
			super(id, name, group);
			this.defaultValue = value;
			this.value = value;
		}

		public boolean get() {
			return value;
		}

		@Override
		public void reset() {
			value = defaultValue;
		}

		@Override
		public JsonElement save() {
			return new JsonPrimitive(value);
		}

		@Override
		public void load(JsonElement json) {
			if (json.isJsonPrimitive()) value = json.getAsBoolean();
		}
	}

	public static final class Slider extends Setting {
		public final float min, max, step, defaultValue;
		/** Format for the value badge, for example "%.2f s". */
		public final String format;
		public float value;

		public Slider(String id, String name, String group, float min, float max, float step, float value, String format) {
			super(id, name, group);
			this.min = min;
			this.max = max;
			this.step = step;
			this.defaultValue = value;
			this.format = format;
			this.value = value;
		}

		public float get() {
			return value;
		}

		public String display() {
			return String.format(Locale.ROOT, format, value);
		}

		@Override
		public void reset() {
			value = defaultValue;
		}

		public void set(float v) {
			v = Math.max(min, Math.min(max, v));
			if (step > 0) v = Math.round((v - min) / step) * step + min;
			value = Math.max(min, Math.min(max, v));
		}

		public float fraction() {
			return (value - min) / (max - min);
		}

		@Override
		public JsonElement save() {
			return new JsonPrimitive(value);
		}

		@Override
		public void load(JsonElement json) {
			if (json.isJsonPrimitive()) set(json.getAsFloat());
		}
	}

	/** Any number of options selected. */
	public static final class Chips extends Setting {
		public final List<String> options;
		public final List<String> defaultSelected;
		public final Set<String> selected = new LinkedHashSet<>();

		public Chips(String id, String name, String group, List<String> options, List<String> selected) {
			super(id, name, group);
			this.options = options;
			this.defaultSelected = List.copyOf(selected);
			this.selected.addAll(selected);
		}

		/** Old names of options, so a renamed option stays selected in saved settings. */
		private final java.util.Map<String, String> renames = new java.util.HashMap<>();

		public Chips renamed(String from, String to) {
			renames.put(from, to);
			return this;
		}

		public boolean has(String option) {
			return selected.contains(option);
		}

		@Override
		public void reset() {
			selected.clear();
			selected.addAll(defaultSelected);
		}

		public void flip(String option) {
			if (!selected.remove(option)) selected.add(option);
		}

		@Override
		public JsonElement save() {
			JsonArray arr = new JsonArray();
			for (String s : options) if (selected.contains(s)) arr.add(s);
			return arr;
		}

		@Override
		public void load(JsonElement json) {
			if (!json.isJsonArray()) return;
			selected.clear();
			for (JsonElement e : json.getAsJsonArray()) {
				String option = renames.getOrDefault(e.getAsString(), e.getAsString());
				if (options.contains(option)) selected.add(option);
			}
		}
	}

	/** Exactly one option selected. */
	public static final class Choice extends Setting {
		public final List<String> options;
		public final String defaultValue;
		public String value;

		public Choice(String id, String name, String group, List<String> options, String value) {
			super(id, name, group);
			this.options = options;
			this.defaultValue = value;
			this.value = value;
		}

		public String get() {
			return value;
		}

		public boolean is(String option) {
			return value.equals(option);
		}

		public void set(String option) {
			if (options.contains(option)) value = option;
		}

		@Override
		public void reset() {
			value = defaultValue;
		}

		@Override
		public JsonElement save() {
			return new JsonPrimitive(value);
		}

		@Override
		public void load(JsonElement json) {
			if (json.isJsonPrimitive()) set(json.getAsString());
		}
	}

	/** A short line of text. */
	public static final class Text extends Setting {
		public final String defaultValue;
		public final String placeholder;
		public final int maxLength;
		public final Predicate<Character> allowed;
		public String value;

		public Text(String id, String name, String group, String value, String placeholder, int maxLength, Predicate<Character> allowed) {
			super(id, name, group);
			this.defaultValue = value;
			this.placeholder = placeholder;
			this.maxLength = maxLength;
			this.allowed = allowed;
			this.value = value;
		}

		public String get() {
			return value;
		}

		@Override
		public void reset() {
			value = defaultValue;
		}

		@Override
		public JsonElement save() {
			return new JsonPrimitive(value);
		}

		@Override
		public void load(JsonElement json) {
			if (!json.isJsonPrimitive()) return;
			String v = json.getAsString();
			value = v.length() > maxLength ? v.substring(0, maxLength) : v;
		}
	}
}
