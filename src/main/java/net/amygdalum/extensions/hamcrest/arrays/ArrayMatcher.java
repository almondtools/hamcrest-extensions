package net.amygdalum.extensions.hamcrest.arrays;

import static java.util.Arrays.asList;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.hamcrest.core.IsNull.nullValue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.StringDescription;
import org.hamcrest.TypeSafeMatcher;
import org.hamcrest.core.IsNull;

import net.amygdalum.extensions.hamcrest.util.Matches;

public class ArrayMatcher<T> extends TypeSafeMatcher<T[]> {

	private Class<T> type;
	private Mode<T> match;
	private List<Matcher<T>> elements;

	public ArrayMatcher(Class<T> type) {
		this.type = type;
		this.match = new Exact();
		this.elements = new ArrayList<>();
	}

	public ArrayMatcher<T> element(T element) {
		return element(match(element));
	}

	public ArrayMatcher<T> element(Matcher<T> element) {
		elements.add(element);
		return this;
	}

	private Matcher<T> match(T element) {
		if (element == null) {
			return nullValue(type);
		} else {
			return equalTo(element);
		}
	}

	@Override
	public void describeTo(Description description) {
		description.appendValue(elements);
	}

	@Override
	protected void describeMismatchSafely(T[] item, Description mismatchDescription) {
		Matches<T> matches = new Matches<>();

		Iterator<Matcher<T>> elementIterator = elements.iterator();
		Iterator<? extends T> itemIterator = asList(item).iterator();
		while (elementIterator.hasNext() && itemIterator.hasNext()) {
			Matcher<T> matcher = elementIterator.next();
			T element = itemIterator.next();
			if (!matcher.matches(element)) {
				matches.mismatch(matcher, element);
			} else {
				matches.match();
			}
		}
		if (elementIterator.hasNext()) {
			int count = count(elementIterator);
			matches.mismatch("missing " + count + " elements");
		}
		if (itemIterator.hasNext()) {
			List<T> items = collect(itemIterator);
			matches.mismatch("found " + items.size() + " elements surplus " + toDescriptionSet(items));
		}

		if (matches.containsMismatches()) {
			mismatchDescription.appendText("mismatching elements ").appendDescriptionOf(matches);
		}
	}

	private int count(Iterator<?> iterator) {
		int count = 0;
		while (iterator.hasNext()) {
			iterator.next();
			count++;
		}
		return count;
	}

	private List<T> collect(Iterator<? extends T> iterator) {
		List<T> collected = new ArrayList<>();
		while (iterator.hasNext()) {
			collected.add(iterator.next());
		}
		return collected;
	}

	private Set<String> toDescriptionSet(List<T> elements) {
		Matcher<T> matcher = bestMatcher();
		Set<String> set = new LinkedHashSet<>();
		for (T element : elements) {
			String desc = descriptionOf(matcher, element);
			set.add(desc);
		}
		return set;
	}

	private Matcher<T> bestMatcher() {
		for (Matcher<T> matcher : elements) {
			if (matcher.getClass() != IsNull.class) {
				return matcher;
			}
		}
		return equalTo(null);
	}

	private <S> String descriptionOf(Matcher<S> matcher, S value) {
		StringDescription description = new StringDescription();
		matcher.describeMismatch(value, description);
		return description.toString();
	}

	@Override
	protected boolean matchesSafely(T[] item) {
		return match.matchesSafely(item);
	}

	@SuppressWarnings("unchecked")
	@SafeVarargs
	public static <T> ArrayMatcher<T> arrayContaining(Class<T> key, Object... elements) {
		ArrayMatcher<T> set = new ArrayMatcher<>(key);
		for (Object element : elements) {
			if (element instanceof Matcher) {
				set.element((Matcher<T>) element);
			} else {
				set.element(key.cast(element));
			}
		}
		return set;
	}

	public ArrayMatcher<T> inAnyOrder() {
		this.match = new AnyOrder();
		return this;
	}

	public ArrayMatcher<T> atLeast() {
		this.match = new AtLeast();
		return this;
	}

	interface Mode<T> {
		public abstract boolean matchesSafely(T[] item);
	}

	private class Exact implements Mode<T> {

		@Override
		public boolean matchesSafely(T[] item) {
			if (item.length != elements.size()) {
				return false;
			}
			Iterator<Matcher<T>> elementIterator = elements.iterator();
			Iterator<? extends T> itemIterator = Arrays.asList(item).iterator();
			while (elementIterator.hasNext() && itemIterator.hasNext()) {
				Matcher<T> matcher = elementIterator.next();
				T element = itemIterator.next();
				if (!matcher.matches(element)) {
					return false;
				}
			}
			return true;
		}

	}

	private class AnyOrder implements Mode<T> {

		@Override
		public boolean matchesSafely(T[] item) {
			if (item.length != elements.size()) {
				return false;
			}
			List<Matcher<T>> pending = new ArrayList<>(elements);
			Iterator<? extends T> itemIterator = Arrays.asList(item).iterator();
			nextItem: while (itemIterator.hasNext()) {
				T element = itemIterator.next();
				Iterator<Matcher<T>> elementIterator = pending.iterator();
				while (elementIterator.hasNext()) {
					Matcher<T> matcher = elementIterator.next();
					if (matcher.matches(element)) {
						elementIterator.remove();
						continue nextItem;
					}
				}
				return false;
			}
			return pending.isEmpty();
		}

	}

	private class AtLeast implements Mode<T> {

		@Override
		public boolean matchesSafely(T[] item) {
			if (item.length < elements.size()) {
				return false;
			}
			List<Matcher<T>> pending = new ArrayList<>(elements);
			Iterator<? extends T> itemIterator = Arrays.asList(item).iterator();
			nextItem: while (itemIterator.hasNext()) {
				T element = itemIterator.next();
				Iterator<Matcher<T>> elementIterator = pending.iterator();
				while (elementIterator.hasNext()) {
					Matcher<T> matcher = elementIterator.next();
					if (matcher.matches(element)) {
						elementIterator.remove();
						continue nextItem;
					}
				}
				continue;
			}
			return pending.isEmpty();
		}

	}
}
