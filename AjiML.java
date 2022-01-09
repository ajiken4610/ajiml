package com.ajiken4610.gachasys;

import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.SpannedString;
import android.text.style.BackgroundColorSpan;
import android.text.style.BulletSpan;
import android.text.style.ClickableSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.LeadingMarginSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StrikethroughSpan;
import android.text.style.StyleSpan;
import android.text.style.SubscriptSpan;
import android.text.style.SuperscriptSpan;
import android.text.style.TypefaceSpan;
import android.text.style.URLSpan;
import android.text.style.UnderlineSpan;
import android.util.Log;
import android.view.View;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class AjiML {
	private AjiML() {
		new IllegalAccessError("Cannnot new AjiML class.");
	}

	public static Spanned fromAjiML(String text, SpanPickerListenerSet pickerListenerSet) {
		AjiMLSolver solver = new AjiMLSolver(text);
		solver.setPickerListenerSet(pickerListenerSet);
		// 隗｣譫�
		return solver.solve();
	}

	public static Spanned fromAjiML(String text) {
		return fromAjiML(text, new SpanPickerListenerSet());
	}

	public static Map<String, Spanned> fromSplitedAjiML(String text, SpanPickerListenerSet pickerListenerSet) {

		SplitedAjiMLSolver solver = new SplitedAjiMLSolver(text);
		solver.setPickerListenerSet(pickerListenerSet);
		try {
			return solver.solve();
		} catch (Exception e) {
			Map<String, Spanned> map = new HashMap<>();
			map.put("main", new SpannedString(traceString(e)));
			return map;
		}
	}

	private static String traceString(Exception e) {
		StringWriter writer = new StringWriter();
		PrintWriter pWriter = new PrintWriter(writer);
		e.printStackTrace(pWriter);
		return writer.toString();
	}

	public static Map<String, Spanned> fromSplitedAjiML(String text) {
		return fromSplitedAjiML(text, new SpanPickerListenerSet());
	}

	public static class SplitedAjiMLSolver {
		private String text;
		private StringIterator iterator;
		private Map<String, Spanned> spannableMap = new HashMap<>();
		private Map<String, String> stringMap = new HashMap<>();
		private SpanPickerListenerSet pickerListenerSet = new SpanPickerListenerSet();

		private CharEffectSet grobalEffectSet = new CharEffectSet();

		private boolean isSolved = false;

		public SplitedAjiMLSolver(String text) {
			this.text = text;
		}

		public void setPickerListenerSet(SpanPickerListenerSet newSet) {
			this.pickerListenerSet = newSet;
		}

		public Map<String, Spanned> solve() {
			if (isSolved) {
				return spannableMap;
			}
			iterator = new StringIterator(text);
			int currentSpecialInt;
			while ((currentSpecialInt = passBeforeSpecialCharacter()) != -1) {
				switch (currentSpecialInt) {
				case (int) '&':
					onDetectAnd();
					break;
				case (int) '$':
					onDetectDollar();
					break;
				}
			}
			for (String divName : stringMap.keySet()) {
				AjiMLSolver solver = new AjiMLSolver(stringMap.get(divName));
				SpanPickerListenerSet newSet = pickerListenerSet.clone();
				newSet.effectPicker = new AjiML.CharEffectPicker() {
					@Override
					public CharEffect pick(String key) {
						if (grobalEffectSet.has(key)) {
							return grobalEffectSet.getEffect(key);
						} else if (pickerListenerSet.effectPicker != null) {
							CharEffect ret = pickerListenerSet.effectPicker.pick(key);

							return ret;
						}
						return null;
					}
				};
				solver.setPickerListenerSet(newSet);
				spannableMap.put(divName, solver.solve());
			}
			isSolved = true;
			return spannableMap;
		}

		private void onDetectAnd() {
			String divName = getBefore('[');
			String divText = getBefore(']');
			stringMap.put(divName, divText);
		}

		private void onDetectDollar() {
			String effectName = getBefore('{');
			String effectValue = getBefore('}');
			grobalEffectSet.addEffect(effectName, new CharEffect(effectValue, pickerListenerSet));
		}

		private String getBefore(char detect) {
			// append縺励↑縺�縺ｮ縺ｧfalse
			iterator.setIsAppending(false);
			StringBuilder retBuilder = new StringBuilder();
			boolean isInDynamicTag = false;
			while (iterator.hasNext()) {
				char current = iterator.next();
				if (current == '!') {
					isInDynamicTag = !isInDynamicTag;
				}
				if ((!isInDynamicTag) && current == detect)
					break;
				retBuilder.append(current);
			}
			return retBuilder.toString();
		}

		private static char[] SPECIAL_CHARACTERS = { '&', '$' };

		private int passBeforeSpecialCharacter() {
			iterator.setIsAppending(false);
			int ret = -1;
			loop: while (iterator.hasNext()) {
				char current = iterator.next();
				// 迚ｹ谿頑枚蟄励↓縺ゅ◆縺｣縺溘ｉ縺昴ｌ繧定ｿ斐☆
				for (char currentSpecial : SPECIAL_CHARACTERS) {
					if (currentSpecial == current) {
						ret = currentSpecial;
						break loop;
					}
				}
			}
			return ret;
		}
	}

	public static class AjiMLSolver {
		private String text;
		private SpannableStringBuilder builder = new SpannableStringBuilder();
		private List<CharEffect> effects = new ArrayList<>();
		private StringIterator iterator;
		private int nestCount = 0;
		private CharEffectSet effectSet = new CharEffectSet();
		private SpanPickerListenerSet pickerListenerSet = new SpanPickerListenerSet();

		private boolean isSolved = false;

		public AjiMLSolver(String text) {
			this.text = text;
		}

		public void setPickerListenerSet(SpanPickerListenerSet newSet) {
			this.pickerListenerSet = newSet;
		}

		public Spanned solve() {
			if (isSolved) {
				return builder;
			}
			removeDoubleSpaceAndBreak();
			iterator = new StringIterator(text);
			buildSpannable();
			insertEffects();
			isSolved = true;
			return builder;
		}

		private void insertEffects() { // 繧ｨ繝輔ぉ繧ｯ繝医ｒ謖ｿ蜈･
			for (CharEffect effect : effects) {
				for (Object what : effect.effects) {
					builder.setSpan(what, effect.start, effect.end, Spanned.SPAN_INCLUSIVE_EXCLUSIVE);
				}
			}
		}

		private void removeDoubleSpaceAndBreak() {
			StringIterator iterator = new StringIterator(text);
			boolean lastIsSpace = false;
			StringBuilder retBuilder = new StringBuilder(text.length());
			while (iterator.hasNext()) {
				char current = iterator.next();
				if (!(lastIsSpace && current == ' ' || current == '\n')) {
					retBuilder.append(current);
				}
				lastIsSpace = current == ' ';
			}
			text = retBuilder.toString();
		}

		private void buildSpannable() {
			// 邨ゅｊ縺梧擂繧九∪縺ｧ郢ｰ繧願ｿ斐☆
			// 繧ｨ繝輔ぉ繧ｯ繝医�ｮ遞ｮ鬘槭→蝣ｴ謇�繧定ｪｿ縺ｹ繧�
			int currentSpecialInt;
			while ((currentSpecialInt = appendBeforeSpecialCharacter()) != -1) {
				switch (currentSpecialInt) {
				case (int) '#':
					CharEffect effect = onDetectSharp();
					effect.start = iterator.getAppendedCount();
					effects.add(effect);
					nestCount++;
					buildSpannable();
					effect.end = iterator.getAppendedCount();
					break;
				case (int) '}':
					if (nestCount > 0) {
						nestCount--;
						return;
					}
					builder.append('}');
					iterator.addAppendedCount(1);
					break;
				case (int) '$':
					onDetectDollar();
					break;
				}
			}
			iterator.setIsAppending(false);
		}

		private void onDetectDollar() {

			String effectName = getBefore('{');
			String effectValue = getBefore('}');
			effectSet.addEffect(effectName, new CharEffect(effectValue, pickerListenerSet));
		}

		private CharEffect onDetectSharp() {
			String effectValue = getBefore('{');
			if (effectSet.has(effectValue)) {
				return effectSet.getEffect(effectValue).clone();
			} else if (pickerListenerSet.effectPicker != null) {
				CharEffect ret = pickerListenerSet.effectPicker.pick(effectValue);
				if (ret != null) {
					return ret.clone();
				}
			}
			return new CharEffect(effectValue, pickerListenerSet);
		}

		private static char[] SPECIAL_CHARACTERS = { '#', '$', '}' };

		private int appendBeforeSpecialCharacter() {
			// append縺吶ｋ縺ｮ縺ｧtrue
			iterator.setIsAppending(true);
			int ret = -1;
			loop: while (iterator.hasNext()) {
				char current = iterator.next();
				// 迚ｹ谿頑枚蟄励↓縺ゅ◆縺｣縺溘ｉ縺昴ｌ繧定ｿ斐☆
				for (char currentSpecial : SPECIAL_CHARACTERS) {
					if (currentSpecial == current) {
						ret = currentSpecial;
						iterator.decrementAppendedCount();
						break loop;
					}
				}
				// !縺ｫ縺ゅ◆縺｣縺溘ｉ鄂ｮ謠�
				if (current == '!') {
					appendDynamicChar();
				} else {
					builder.append(current);
				}
			}
			return ret;
		}

		private static Map<String, CharSequence> defaultDynamicCharMap = new HashMap<>();

		static {
			defaultDynamicCharMap.put("", "!");
			defaultDynamicCharMap.put("br", System.lineSeparator());
			defaultDynamicCharMap.put("sp", " ");
			defaultDynamicCharMap.put("idsp", "　");
			defaultDynamicCharMap.put("#", "#");
			defaultDynamicCharMap.put("$", "$");
			defaultDynamicCharMap.put("}", "}");
			defaultDynamicCharMap.put("{", "{");
			defaultDynamicCharMap.put("|", "|");
			defaultDynamicCharMap.put("&", "&");
			defaultDynamicCharMap.put("[", "[");
			defaultDynamicCharMap.put("]", "]");
		}

		private void appendDynamicChar() {
			String key = getBefore('!');
			iterator.decrementAppendedCount();
			if (defaultDynamicCharMap.containsKey(key)) {
				CharSequence value = defaultDynamicCharMap.get(key);
				builder.append(value);
				iterator.addAppendedCount(value.length());
			} else if (pickerListenerSet.charPicker != null) {
				CharSequence text = pickerListenerSet.charPicker.pick(key);
				builder.append(text);
				iterator.addAppendedCount(text.length());
			} else {
				CharSequence text = CharSequencePicker.DEFAULT.pick(key);
				builder.append(text);
				iterator.addAppendedCount(text.length());
			}
			iterator.setIsAppending(true);
		}

		private String getBefore(char detect) {
			// append縺励↑縺�縺ｮ縺ｧfalse
			iterator.setIsAppending(false);
			char current;
			StringBuilder retBuilder = new StringBuilder();
			while (iterator.hasNext() && (current = iterator.next()) != detect) {
				retBuilder.append(current);
			}
			return retBuilder.toString();
		}

		private boolean appendBefore(char detect) {
			// append縺吶ｋ縺ｮ縺ｧtrue
			iterator.setIsAppending(true);
			char current = 0;
			while (iterator.hasNext() && (current = iterator.next()) != detect) {
				// !縺ｫ縺ゅ◆縺｣縺溘ｉ鄂ｮ謠�
				if (current == '!') {
					appendDynamicChar();
				} else {
					builder.append(current);
				}
			}
			if (current == detect) {
				iterator.decrementAppendedCount();
			}
			return iterator.hasNext();
		};
	}

	private static class StringIterator implements Iterator<Character> {
		private char[] data;
		private int iterationIndex;
		private int appendedCount;
		private boolean isAppending;

		public StringIterator(String text) {
			data = new char[text.length()];
			text.getChars(0, text.length(), data, 0);
		}

		@Override
		public Character next() {
			char ret = data[iterationIndex];
			iterationIndex++;
			if (isAppending) {
				appendedCount++;
			}
			return ret;
		}

		@Override
		public boolean hasNext() {
			return iterationIndex < data.length;
		}

		public void setIsAppending(boolean isAppending) {
			this.isAppending = isAppending;
		}

		public int getAppendedCount() {
			return appendedCount;
		}

		public int getIterationIndex() {
			if (hasNext()) {
				return iterationIndex;
			} else {
				return -1;
			}
		}

		public void decrementAppendedCount() {
			appendedCount--;
		}

		public void addAppendedCount(int count) {
			appendedCount = appendedCount + count;
		}
	}

	public static class CharEffect implements Cloneable {
		public int start;
		public int end;
		List<Object> effects = new ArrayList<>();

		private static Map<String, Integer> colorNameMap = new HashMap<>();
		private SpanPickerListenerSet pickerListenerSet;
		private String effectText;

		static {
			colorNameMap.put("red", 0xffff0000);
			colorNameMap.put("green", 0xff008000);
			colorNameMap.put("lime", 0xff00ff00);
			colorNameMap.put("blue", 0xff0000ff);
			colorNameMap.put("yellow", 0xffffff00);
			colorNameMap.put("cyan", 0xff00ffff);
			colorNameMap.put("magenta", 0xffff00ff);
			colorNameMap.put("black", 0xff000000);
			colorNameMap.put("while", 0xffffffff);
			colorNameMap.put("gray", 0xff808080);
			colorNameMap.put("lightgray", 0xffd3d3d3);
		}

		public CharEffect(String what, SpanPickerListenerSet pickerListenerSet) {
			this.effectText = what;
			this.pickerListenerSet = pickerListenerSet;
			// 謗･鬆ｭ霎槭＃縺ｨ縺ｫ繝ｫ繝ｼ繝�
			for (String option : what.split("#")) {
				String[] splitedFont = option.split("\\|");
				String prefix = splitedFont[0];

				if (prefix.equalsIgnoreCase("b")) {
					addEffect(new StyleSpan(Typeface.BOLD));
				} else if (prefix.equalsIgnoreCase("i")) {
					addEffect(new StyleSpan(Typeface.ITALIC));
				} else if (prefix.equalsIgnoreCase("u")) {
					addEffect(new UnderlineSpan());
				} else if (prefix.equalsIgnoreCase("s")) {
					addEffect(new StrikethroughSpan());
				} else if (prefix.equalsIgnoreCase("big")) {
					addEffect(new RelativeSizeSpan(1.25f));
				} else if (prefix.equalsIgnoreCase("small")) {
					addEffect(new RelativeSizeSpan(0.8f));
				} else if (prefix.equalsIgnoreCase("size")) {
					if (splitedFont.length > 1) {
						try {
							addEffect(new RelativeSizeSpan(Float.parseFloat(splitedFont[1])));
						} catch (NumberFormatException e) {
						}
					}
				} else if (prefix.equalsIgnoreCase("colname")) {
					if (splitedFont.length > 1 && colorNameMap.containsKey(splitedFont[1])) {
						addEffect(new ForegroundColorSpan(colorNameMap.get(splitedFont[1])));
					}
				} else if (prefix.equalsIgnoreCase("colcode")) {
					if (splitedFont.length > 1 && splitedFont[1].length() == 6) {
						try {
							addEffect(new ForegroundColorSpan(Integer.parseInt(splitedFont[1], 16) | 0xff000000));
						} catch (NumberFormatException e) {
						}
					}
				} else if (prefix.equalsIgnoreCase("ul")) {
					addEffect(new BulletSpan());
				} else if (prefix.equalsIgnoreCase("indent")) {
					int width = 10;
					if (splitedFont.length > 1) {
						try {
							width = Integer.parseInt(splitedFont[1]);
						} catch (NumberFormatException e) {
						}
					}
					addEffect(new LeadingMarginSpan.Standard(width));
				} else if (prefix.equalsIgnoreCase("link")) {
					if (splitedFont.length > 1) {
						addEffect(new URLSpan(splitedFont[1]));
					}
				} else if (prefix.equalsIgnoreCase("sub")) {
					addEffect(new SubscriptSpan());
				} else if (prefix.equalsIgnoreCase("sup")) {
					addEffect(new SuperscriptSpan());
				} else if (prefix.equalsIgnoreCase("mono")) {
					addEffect(new TypefaceSpan("monospace"));
				} else if (prefix.equalsIgnoreCase("backcolname")) {
					if (splitedFont.length > 1 && colorNameMap.containsKey(splitedFont[1])) {
						addEffect(new BackgroundColorSpan(colorNameMap.get(splitedFont[1])));
					}
				} else if (prefix.equalsIgnoreCase("backcolcode")) {
					if (splitedFont.length > 1 && splitedFont[1].length() == 6) {
						try {
							addEffect(new BackgroundColorSpan(Integer.parseInt(splitedFont[1], 16) | 0xff000000));
						} catch (NumberFormatException e) {

						}
					}
				} else if (prefix.equalsIgnoreCase("clickable")) {

					class CustomClickableSpan extends ClickableSpan {
						private OnSpanClickListener listener;
						private String key;

						public CustomClickableSpan(OnSpanClickListener listener, String key) {
							this.listener = listener;
							this.key = key;
						}

						@Override
						public void onClick(View v) {
							listener.onClick(key);
						}
					}
					String key = "";
					if (splitedFont.length > 1) {
						key = splitedFont[1];
					}
					addEffect(new CustomClickableSpan(pickerListenerSet.clickListener, key));
				}
			}
		}

		public CharEffect(String what) {
			this(what, new SpanPickerListenerSet());
		}

		public void addEffect(Object what) {
			effects.add(what);
		}

		@Override
		public String toString() {
			return String.format("start=%1$d,end=%2$d,count=%3$d", start, end, effects.size());
		}

		@Override
		public CharEffect clone() {
			return new CharEffect(effectText, pickerListenerSet);
		}
	}

	public static class CharEffectSet {
		private Map<String, CharEffect> sets = new HashMap<>();

		public Map<String, CharEffect> getSets() {
			return sets;
		}

		public void addEffect(String key, CharEffect value) {
			sets.put(key, value);
		}

		public void addEffects(Map<String, CharEffect> addSets) {
			sets.putAll(addSets);
		}

		public void addEffectSet(CharEffectSet addSet) {
			sets.putAll(addSet.getSets());
		}

		public CharEffect getEffect(String key) {
			return sets.get(key);
		}

		public boolean has(String key) {
			return sets.containsKey(key);
		}
	}

	public static interface CharSequencePicker {
		public CharSequence pick(String key);

		public static final CharSequencePicker DEFAULT = new CharSequencePicker() {
			@Override
			public CharSequence pick(String key) {
				return fromAjiML("#colname|red#i#u#b{?" + key + "?}");
			}
		};
	}

	public static interface OnSpanClickListener {
		public void onClick(String key);

		public static final OnSpanClickListener DEFAULT = new OnSpanClickListener() {
			@Override
			public void onClick(String key) {
				Log.d("AjiML", "Span Clicked : " + key);
			}
		};
	}

	public abstract static class CharEffectPicker {

		public abstract CharEffect pick(String key);

		public static final CharEffectPicker DEFAULT = new CharEffectPicker() {
			@Override
			public CharEffect pick(String key) {
				return null;
			}
		};
	}

	public static interface BitmapPicker {
		public Bitmap pick(String key);
	}

	public static class SpanPickerListenerSet implements Cloneable {
		public CharSequencePicker charPicker = CharSequencePicker.DEFAULT;
		public CharEffectPicker effectPicker = CharEffectPicker.DEFAULT;
		public OnSpanClickListener clickListener = OnSpanClickListener.DEFAULT;
		public BitmapPicker bitmapPicker;

		public SpanPickerListenerSet clone() {
			SpanPickerListenerSet ret = new SpanPickerListenerSet();
			ret.charPicker = charPicker;
			ret.effectPicker = effectPicker;
			ret.clickListener = clickListener;
			ret.bitmapPicker = bitmapPicker;
			return ret;
		}
	}
}
