/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package org.elasticsearch.xcontent.yaml;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import org.elasticsearch.xcontent.XContent;
import org.elasticsearch.xcontent.XContentBuilder;
import org.elasticsearch.xcontent.XContentGenerator;
import org.elasticsearch.xcontent.XContentParser;
import org.elasticsearch.xcontent.XContentParserConfiguration;
import org.elasticsearch.xcontent.XContentType;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.util.Set;

/**
 * A YAML based content implementation using Jackson.
 */
public class YamlXContent implements XContent {

    public static XContentBuilder contentBuilder() throws IOException {
        return XContentBuilder.builder(yamlXContent);
    }

    static final YAMLFactory yamlFactory;
    public static final YamlXContent yamlXContent;

    static {
        yamlFactory = new YAMLFactory();
        yamlFactory.configure(JsonParser.Feature.STRICT_DUPLICATE_DETECTION, true);
        yamlXContent = new YamlXContent();
    }

    private YamlXContent() {}

    @Override
    public XContentType type() {
        return XContentType.YAML;
    }

    @Override
    public byte streamSeparator() {
        throw new UnsupportedOperationException("yaml does not support stream parsing...");
    }

    @Override
    public boolean detectContent(byte[] bytes, int offset, int length) {
        return length > 2 && bytes[offset] == '-' && bytes[offset + 1] == '-' && bytes[offset + 2] == '-';
    }

    @Override
    public boolean detectContent(CharSequence chars) {
        return chars.length() > 2 && chars.charAt(0) == '-' && chars.charAt(1) == '-' && chars.charAt(2) == '-';
    }

    @Override
    public XContentGenerator createGenerator(OutputStream os, Set<String> includes, Set<String> excludes) throws IOException {
        return new YamlXContentGenerator(yamlFactory.createGenerator(os, JsonEncoding.UTF8), os, includes, excludes);
    }

    @Override
    public XContentParser createParser(XContentParserConfiguration config, String content) throws IOException {
        return new YamlXContentParser(config, yamlFactory.createParser(content));
    }

    @Override
    public XContentParser createParser(XContentParserConfiguration config, InputStream is) throws IOException {
        return new YamlXContentParser(config, yamlFactory.createParser(is));
    }

    @Override
    public XContentParser createParser(XContentParserConfiguration config, byte[] data, int offset, int length) throws IOException {
        return new YamlXContentParser(config, yamlFactory.createParser(data, offset, length));
    }

    @Override
    public XContentParser createParser(XContentParserConfiguration config, Reader reader) throws IOException {
        return new YamlXContentParser(config, yamlFactory.createParser(reader));
    }
}
