/*
 * Copyright (C) 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.util;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.xmlpull.v1.XmlPullParser.END_DOCUMENT;
import static org.xmlpull.v1.XmlPullParser.END_TAG;
import static org.xmlpull.v1.XmlPullParser.START_DOCUMENT;
import static org.xmlpull.v1.XmlPullParser.START_TAG;
import static org.xmlpull.v1.XmlPullParser.TEXT;

import android.os.PersistableBundle;

import androidx.test.runner.AndroidJUnit4;

import com.android.internal.util.XmlUtils;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

@RunWith(AndroidJUnit4.class)
public class XmlTest {
    @Test
    public void testLargeValues_Normal() throws Exception {
        doLargeValues(XmlUtils.makeTyped(Xml.newSerializer()),
                XmlUtils.makeTyped(Xml.newPullParser()));
    }

    @Test
    public void testLargeValues_Fast() throws Exception {
        doLargeValues(Xml.newFastSerializer(),
                Xml.newFastPullParser());
    }

    /**
     * Verify that we can write and read large {@link String} and {@code byte[]}
     * without issues.
     */
    private static void doLargeValues(TypedXmlSerializer out, TypedXmlPullParser in)
            throws Exception {
        final char[] chars = new char[32_768];
        Arrays.fill(chars, '!');

        final String string = new String(chars);
        final byte[] bytes = string.getBytes();
        assertEquals(chars.length, bytes.length);

        final ByteArrayOutputStream os = new ByteArrayOutputStream();
        out.setOutput(os, StandardCharsets.UTF_8.name());
        out.startTag(null, "tag");
        out.attribute(null, "string", string);
        out.attributeBytesBase64(null, "bytes", bytes);
        out.endTag(null, "tag");
        out.flush();

        final ByteArrayInputStream is = new ByteArrayInputStream(os.toByteArray());
        in.setInput(is, StandardCharsets.UTF_8.name());
        assertNext(in, START_TAG, "tag", 1);
        assertEquals(2, in.getAttributeCount());
        assertEquals(string, in.getAttributeValue(null, "string"));
        assertArrayEquals(bytes, in.getAttributeBytesBase64(null, "bytes"));
    }

    @Test
    public void testPersistableBundle_Normal() throws Exception {
        doPersistableBundle(XmlUtils.makeTyped(Xml.newSerializer()),
                XmlUtils.makeTyped(Xml.newPullParser()));
    }

    @Test
    public void testPersistableBundle_Fast() throws Exception {
        doPersistableBundle(Xml.newFastSerializer(),
                Xml.newFastPullParser());
    }

    /**
     * Verify that a complex {@link PersistableBundle} can be serialized out and
     * then parsed in with the original structure intact.
     */
    private static void doPersistableBundle(TypedXmlSerializer out, TypedXmlPullParser in)
            throws Exception {
        final PersistableBundle expected = buildPersistableBundle();
        final byte[] raw = doPersistableBundleWrite(out, expected);

        // Yes, this string-based check is fragile, but kindofEquals() is broken
        // when working with nested objects and arrays
        final PersistableBundle actual = doPersistableBundleRead(in, raw);
        assertEquals(expected.toString(), actual.toString());
    }

    private static PersistableBundle buildPersistableBundle() {
        final PersistableBundle outer = new PersistableBundle();

        outer.putBoolean("boolean", true);
        outer.putInt("int", 42);
        outer.putLong("long", 43L);
        outer.putDouble("double", 44d);
        outer.putString("string", "com.example <and></and> &amp; more");

        outer.putBooleanArray("boolean[]", new boolean[] { true, false, true });
        outer.putIntArray("int[]", new int[] { 42, 43, 44 });
        outer.putLongArray("long[]", new long[] { 43L, 44L, 45L });
        outer.putDoubleArray("double[]", new double[] { 43d, 44d, 45d });
        outer.putStringArray("string[]", new String[] { "foo", "bar", "baz" });

        final PersistableBundle nested = new PersistableBundle();
        nested.putString("nested_key", "nested_value");
        outer.putPersistableBundle("nested", nested);

        return outer;
    }

    private static byte[] doPersistableBundleWrite(TypedXmlSerializer out, PersistableBundle bundle)
            throws Exception {
        // We purposefully omit START/END_DOCUMENT events here to verify correct
        // behavior of what PersistableBundle does internally
        final ByteArrayOutputStream os = new ByteArrayOutputStream();
        out.setOutput(os, StandardCharsets.UTF_8.name());
        out.startTag(null, "bundle");
        bundle.saveToXml(out);
        out.endTag(null, "bundle");
        out.flush();
        return os.toByteArray();
    }

    private static PersistableBundle doPersistableBundleRead(TypedXmlPullParser in, byte[] raw)
            throws Exception {
        final ByteArrayInputStream is = new ByteArrayInputStream(raw);
        in.setInput(is, StandardCharsets.UTF_8.name());
        in.next();
        return PersistableBundle.restoreFromXml(in);
    }

    @Test
    public void testVerify_Normal() throws Exception {
        doVerify(XmlUtils.makeTyped(Xml.newSerializer()),
                XmlUtils.makeTyped(Xml.newPullParser()));
    }

    @Test
    public void testVerify_Fast() throws Exception {
        doVerify(Xml.newFastSerializer(),
                Xml.newFastPullParser());
    }

    /**
     * Verify that example test data is correctly serialized and parsed
     * end-to-end using the given objects.
     */
    private static void doVerify(TypedXmlSerializer out, TypedXmlPullParser in) throws Exception {
        final ByteArrayOutputStream os = new ByteArrayOutputStream();
        out.setOutput(os, StandardCharsets.UTF_8.name());
        doVerifyWrite(out);
        out.flush();

        final ByteArrayInputStream is = new ByteArrayInputStream(os.toByteArray());
        in.setInput(is, StandardCharsets.UTF_8.name());
        doVerifyRead(in);
    }

    private static final String TEST_STRING = "com.example";
    private static final byte[] TEST_BYTES = new byte[] { 0, 1, 2, 3, 4, 3, 2, 1, 0 };

    private static void doVerifyWrite(TypedXmlSerializer out) throws Exception {
        out.startDocument(StandardCharsets.UTF_8.name(), true);
        out.startTag(null, "one");
        {
            out.startTag(null, "two");
            {
                out.attribute(null, "string", TEST_STRING);
                out.attribute(null, "stringNumber", "49");
                out.attributeBytesHex(null, "bytesHex", TEST_BYTES);
                out.attributeBytesBase64(null, "bytesBase64", TEST_BYTES);
                out.attributeInt(null, "int", 43);
                out.attributeIntHex(null, "intHex", 44);
                out.attributeLong(null, "long", 45L);
                out.attributeLongHex(null, "longHex", 46L);
                out.attributeFloat(null, "float", 47f);
                out.attributeDouble(null, "double", 48d);
                out.attributeBoolean(null, "boolean", true);
            }
            out.endTag(null, "two");

            out.startTag(null, "three");
            {
                out.text("foo");
                out.startTag(null, "four");
                {
                }
                out.endTag(null, "four");
                out.text("bar");
                out.text("baz");
            }
            out.endTag(null, "three");
        }
        out.endTag(null, "one");
        out.endDocument();
    }

    private static void doVerifyRead(TypedXmlPullParser in) throws Exception {
        assertEquals(START_DOCUMENT, in.getEventType());
        assertNext(in, START_TAG, "one", 1);
        {
            assertNext(in, START_TAG, "two", 2);
            {
                assertEquals(11, in.getAttributeCount());
                assertEquals(TEST_STRING, in.getAttributeValue(null, "string"));
                assertArrayEquals(TEST_BYTES, in.getAttributeBytesHex(null, "bytesHex"));
                assertArrayEquals(TEST_BYTES, in.getAttributeBytesBase64(null, "bytesBase64"));
                assertEquals(43, in.getAttributeInt(null, "int"));
                assertEquals(44, in.getAttributeIntHex(null, "intHex"));
                assertEquals(45L, in.getAttributeLong(null, "long"));
                assertEquals(46L, in.getAttributeLongHex(null, "longHex"));
                assertEquals(47f, in.getAttributeFloat(null, "float"), 0.01);
                assertEquals(48d, in.getAttributeDouble(null, "double"), 0.01);
                assertEquals(true, in.getAttributeBoolean(null, "boolean"));

                // Also verify that typed values are available as strings
                assertEquals("000102030403020100", in.getAttributeValue(null, "bytesHex"));
                assertEquals("AAECAwQDAgEA", in.getAttributeValue(null, "bytesBase64"));
                assertEquals("43", in.getAttributeValue(null, "int"));
                assertEquals("2c", in.getAttributeValue(null, "intHex"));
                assertEquals("45", in.getAttributeValue(null, "long"));
                assertEquals("2e", in.getAttributeValue(null, "longHex"));
                assertEquals("true", in.getAttributeValue(null, "boolean"));

                // And that raw strings can be parsed too
                assertEquals("49", in.getAttributeValue(null, "stringNumber"));
                assertEquals(49, in.getAttributeInt(null, "stringNumber"));
            }
            assertNext(in, END_TAG, "two", 2);

            assertNext(in, START_TAG, "three", 2);
            {
                assertNext(in, TEXT);
                assertEquals("foo", in.getText().trim());
                assertNext(in, START_TAG, "four", 3);
                {
                    assertEquals(0, in.getAttributeCount());
                }
                assertNext(in, END_TAG, "four", 3);
                assertNext(in, TEXT);
                assertEquals("barbaz", in.getText().trim());
            }
            assertNext(in, END_TAG, "three", 2);
        }
        assertNext(in, END_TAG, "one", 1);
        assertNext(in, END_DOCUMENT);
    }

    private static void assertNext(TypedXmlPullParser in, int token) throws Exception {
        // We're willing to skip over empty text regions, which some
        // serializers emit transparently
        int event;
        while ((event = in.next()) == TEXT && in.getText().trim().length() == 0) {
        }
        assertEquals("next", token, event);
        assertEquals("getEventType", token, in.getEventType());
    }

    private static void assertNext(TypedXmlPullParser in, int token, String name, int depth)
            throws Exception {
        assertNext(in, token);
        assertEquals("getName", name, in.getName());
        assertEquals("getDepth", depth, in.getDepth());
    }
}
