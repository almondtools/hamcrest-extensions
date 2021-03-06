package net.amygdalum.extensions.hamcrest.objects;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Set;

import org.hamcrest.Description;
import org.hamcrest.TypeSafeMatcher;

public class ReflectiveEqualsMatcher<T> extends TypeSafeMatcher<T> {

	private T object;
	private Set<String> excluded;
	private Set<Class<?>> customBaseTypes;

	public ReflectiveEqualsMatcher(T object) {
		this.object = object;
		this.excluded = new HashSet<>();
		this.customBaseTypes = new HashSet<>();
	}

	public ReflectiveEqualsMatcher<T> excluding(String... excludedFields) {
		excluded.addAll(asList(excludedFields));
		return this;
	}

	public ReflectiveEqualsMatcher<T> withBaseTypes(Class<?>... classes) {
		customBaseTypes.addAll(asList(classes));
		return this;
	}

	@Override
	public void describeTo(Description description) {
		description.appendText("should reflectively equal the given object:\n" + describe(object, new HashSet<>(), 0));
	}

	@Override
	protected void describeMismatchSafely(T item, Description mismatchDescription) {
		mismatchDescription.appendText(", but was:\n" + describe(item, new HashSet<>(), 0));
	}

	private String describe(Object item, Set<Object> done, int indent) {
		if (item == null) {
			return "null";
		} else if (item instanceof String) {
			return "\"" + item + "\"";
		} else if (item instanceof Number || item instanceof Character || item instanceof Boolean) {
			return item.toString();
		} else if (done.contains(item)) {
			return "@";
		} else {
			done.add(item);
		}
		StringBuilder buffer = new StringBuilder(item.getClass().getSimpleName()).append(" {\n");
		for (Field field : fields(item.getClass())) {
			try {
				Object value = field.get(item);
				buffer.append(spaces(indent)).append(field.getName()).append(": ").append(describe(value, done, indent + 2));
			} catch (IllegalArgumentException | IllegalAccessException e) {
				continue;
			}
			buffer.append(",\n");
		}
		buffer.setLength(buffer.length() - 2);
		buffer.append("\n").append(spaces(indent)).append("}");
		return buffer.toString();
	}

	private char[] spaces(int indent) {
		char[] cs = new char[indent];
		Arrays.fill(cs, ' ');
		return cs;
	}

	@Override
	protected boolean matchesSafely(T item) {
		try {
			if (object.getClass() != item.getClass()) {
				return false;
			}
			Set<Comparison> done = new HashSet<>();
			Queue<Comparison> todo = new LinkedList<>();
			todo.add(new Comparison(object.getClass(), object, item));
			while (!todo.isEmpty()) {
				Comparison current = todo.remove();
				if (done.contains(current)) {
					continue;
				} else {
					done.add(current);
				}
				Object left = current.left;
				Object right = current.right;
				if (left == right) {
					continue;
				}
				for (Field field : fields(current.clazz)) {
					Object leftField = field.get(left);
					Object rightField = field.get(right);
					try {
						todo.addAll(compare(leftField, rightField));
					} catch (ComparisonException e) {
						return false;
					}
				}
			}
			return true;
		} catch (ReflectiveOperationException e) {
			return false;
		}
	}

	private List<Comparison> compare(Object leftField, Object rightField) throws ComparisonException {
		if (leftField == null && rightField == null) {
			return emptyList();
		} else if (leftField == null) {
			throw new ComparisonException();
		} else if (rightField == null) {
			throw new ComparisonException();
		} else {
			Class<?> clazz = leftField.getClass();
			if (isBaseType(clazz) || isCustomBaseType(clazz)) {
				if (!leftField.equals(rightField)) {
					throw new ComparisonException();
				}
				return emptyList();
			} else if (clazz.isArray()) {
				List<Comparison> todo = new ArrayList<>();
				if (Array.getLength(leftField) != Array.getLength(rightField)) {
					throw new ComparisonException();
				}
				int length = Array.getLength(leftField);
				for (int i = 0; i < length; i++) {
					Object leftItem = Array.get(leftField, i);
					Object rightItem = Array.get(rightField, i);
					todo.addAll(compare(leftItem, rightItem));
				}
				return todo;
			} else if (Collection.class.isAssignableFrom(clazz)) {
				List<Comparison> todo = new ArrayList<>();
				Collection<?> left = (Collection<?>) leftField;
				Collection<?> right = (Collection<?>) rightField;
				if (left.size() != right.size()) {
					throw new ComparisonException();
				}
				Iterator<?> li = left.iterator();
				Iterator<?> ri = right.iterator();
				while (li.hasNext() && ri.hasNext()) {
					Object leftItem = li.next();
					Object rightItem = ri.next();
					todo.addAll(compare(leftItem, rightItem));
				}
				return todo;
			} else if (leftField.getClass() != rightField.getClass()) {
				throw new ComparisonException();
			} else {
				return asList(new Comparison(clazz, leftField, rightField));
			}
		}
	}

	public boolean isCustomBaseType(Class<?> clazz) {
		return customBaseTypes.stream()
			.anyMatch(base -> base.isAssignableFrom(clazz));
	}

	public boolean isBaseType(Class<?> clazz) {
		return clazz.isPrimitive()
			|| Boolean.class.isAssignableFrom(clazz)
			|| Number.class.isAssignableFrom(clazz)
			|| Character.class.isAssignableFrom(clazz)
			|| String.class.isAssignableFrom(clazz)
			|| clazz == Object.class
			|| clazz == Class.class;
	}

	private List<Field> fields(Class<?> clazz) {
		List<Field> fields = new ArrayList<>();
		while (clazz != null && clazz != Object.class) {
			for (Field field : clazz.getDeclaredFields()) {
				if (field.isSynthetic() || excluded.contains(field.getName())) {
					continue;
				}
				field.setAccessible(true);
				fields.add(field);
			}
			clazz = clazz.getSuperclass();
		}
		return fields;
	}

	public static <T> ReflectiveEqualsMatcher<T> reflectiveEqualTo(T object) {
		return new ReflectiveEqualsMatcher<T>(object);
	}

	private static class Comparison {

		private Class<?> clazz;
		public Object left;
		public Object right;

		public Comparison(Class<?> clazz, Object left, Object right) {
			this.clazz = clazz;
			this.left = left;
			this.right = right;
		}

		@Override
		public int hashCode() {
			return left.hashCode() + right.hashCode();
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj) {
				return true;
			}
			if (obj == null) {
				return false;
			}
			if (getClass() != obj.getClass()) {
				return false;
			}
			Comparison that = (Comparison) obj;
			return this.clazz == that.clazz
				&& this.left == that.left
				&& this.right == that.right;
		}

	}

	private static class ComparisonException extends Exception {

	}

}
