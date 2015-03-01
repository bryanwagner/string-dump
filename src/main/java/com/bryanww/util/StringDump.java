package com.bryanww.util;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Map.Entry;
import java.util.Stack;
import java.util.TreeMap;

/**
 * Utility class to dump the entire object graph of a given object to string using reflection.<p>
 * This version contains a powerful iterative algorithm that can traverse extremely long object graphs,
 * bounded by memory and not runtime stack frames.
 * 
 * @author  Bryan Wagner
 * @since   2013-08-24
 * @version 2013-08-24
 */
public class StringDump {
	
	/**
	 * Iteratively traverses the object graph of the given object, dump all values and references using a custom format. Does not format static fields.<p>
	 * @see #dump(Object, boolean, IdentityHashMap, int)
	 * @param object the {@code Object} to dump
	 * @return a custom-formatted string representing the internal values of the object graph of the given object
	 */
	public static String dump(Object object) {
		return dump(object, false);
	}
	
	/**
	 * Iteratively traverses the object graph of the given object, dump all values and references using a custom format.<p>
	 * Parses all fields of the runtime class including super class fields, which are successively prefixed with "{@code super.}" at each level.<p>
	 * {@code Number}s, {@code enum}s, and {@code null} references are formatted using the standard {@link String#valueOf()} method.
	 * {@code CharSequences}s are wrapped with quotes.<p>
	 * The traversal implements an iterative algorithm based on a stack data structure, so the traversal is limited by memory and not runtime stack frames.<p>
	 * Backwards references are tracked using a "visitor map" which is an instance of {@link IdentityHashMap}.
	 * When an existing object reference is encountered the {@code "sysId"} is printed as a leaf (to avoid infinite loops).<p>
	 * 
	 * @param object             the {@code Object} to dump
	 * @param isIncludingStatics {@code true} if {@code static} fields should be dumped, {@code false} to skip them
	 * @return a custom-formatted string representing the internal values of the object graph of the given object
	 */
	public static String dump(Object object, boolean isIncludingStatics) {
		StringBuilder                   builder    = new StringBuilder();
		Stack<Object[]>                 stack      = new Stack<Object[]>();
		IdentityHashMap<Object, Object> visitorMap = new IdentityHashMap<Object, Object>();
		TreeMap<String, Field>          fieldMap   = new TreeMap<String, Field>();  // can modify this to change or omit the sort order
		ArrayList<Entry<String, Field>> fieldList  = new ArrayList<Entry<String, Field>>();
		
		Object        endArray    = new Object();  // signals the end bracket of an array
		Object        endObject   = new Object();  // signals the end bracket of an object
		StringBuilder emptyString = new StringBuilder();
		stack.push(new Object[] {emptyString, emptyString, object});
		while (!stack.isEmpty()) {
			Object[]      params = stack.pop();
			StringBuilder tabs   = (StringBuilder) params[0];
			StringBuilder label  = (StringBuilder) params[1];
			Object        next   = params[2];
			fieldMap.clear();
			fieldList.clear();
			builder.append(tabs).append(label);
			
			if (next == null ||
					next instanceof Number || next instanceof Character || next instanceof Boolean ||
					next.getClass().isPrimitive() || next.getClass().isEnum()) {
				builder.append(String.valueOf(next));
			}
			else if (next == endArray) {
				builder.append("]");
			}
			else if (next == endObject) {
				builder.append("}");
			}
			else if (next instanceof CharSequence) {
				builder.append("\"").append(next).append("\"");
			}
			else {
				int sysId = System.identityHashCode(next);
				
				if (visitorMap.containsKey(next)) {
					builder.append("(sysId#").append(sysId).append(")");
				}
				else {
					visitorMap.put(next, next);
					
					if (next.getClass().isArray()) {
						builder.append("[").append(next.getClass().getName()).append(":sysId#").append(sysId);
						int length = Array.getLength(next);
						if (length == 0) {
							builder.append("]");
						}
						else {
							stack.push(new Object[] {tabs, emptyString, endArray});  // add on its own line
							StringBuilder nextTab = new StringBuilder().append(tabs).append("\t");
							for (int i = length - 1; i >= 0; i--) {
								Object        arrayObject = Array.get(next, i);
								StringBuilder nextLabel   = new StringBuilder().append("\"").append(i).append("\":");
								stack.push(new Object[] {nextTab, nextLabel, arrayObject});
							}
						}
					}
					else {
						// enumerate the desired fields of the object before accessing
						StringBuilder superPrefix = new StringBuilder();
						for (Class<?> clazz = next.getClass(); clazz != null && !clazz.equals(Object.class); clazz = clazz.getSuperclass()) {
							Field[] fields = clazz.getDeclaredFields();
							for (int i = 0; i < fields.length; i++) {
								Field field = fields[i];
								if (isIncludingStatics || !Modifier.isStatic(field.getModifiers())) {
									fieldMap.put(superPrefix + field.getName(), field);
								}
							}
							superPrefix.append("super.");
						}
						for (Entry<String, Field> entry : fieldMap.entrySet()) {
							fieldList.add(entry);  // add in sorted order
						}
						
						builder.append("{").append(next.getClass().getName()).append(":sysId#").append(sysId);
						if (fieldList.isEmpty()) {
							builder.append("}");
						}
						else {
							stack.push(new Object[] {tabs, emptyString, endObject});  // add on its own line
							StringBuilder nextTab = new StringBuilder().append(tabs).append("\t");
							for (int i = fieldList.size() - 1; i >= 0; i--) {
								Entry<String, Field> entry = fieldList.get(i);
								String               name  = entry.getKey();
								Field                field = entry.getValue();
								Object               fieldObject;
								try {
									boolean wasAccessible = field.isAccessible();
									field.setAccessible(true);
									fieldObject = field.get(next);
									field.setAccessible(wasAccessible);  // the accessibility flag should be restored to its prior ClassLoader state
								}
								catch (Throwable e) {
									fieldObject = "!" + e.getClass().getName() + ":" + e.getMessage();
								}
								StringBuilder nextLabel = new StringBuilder().append("\"").append(name).append("\":");
								stack.push(new Object[] {nextTab, nextLabel, fieldObject});
							}
						}
					}
				}
			}
			if (!stack.isEmpty()) {
				builder.append("\n");
			}
		}
		return builder.toString();
	}
	
	public static void main(String[] args) throws Exception {
		
		System.out.println(dump(java.awt.Color.GRAY, true));
		System.out.println();
		System.out.println(dump(Thread.currentThread().getClass()));
		System.out.println();
		System.out.println(dump(Thread.currentThread().getContextClassLoader()));
		System.out.println();
		System.out.println(dump(Thread.currentThread()));
		try {
			throw new RuntimeException();
		}
		catch (Exception e) {
			System.out.println();
			System.out.println(dump(e));
		}
	}
}
