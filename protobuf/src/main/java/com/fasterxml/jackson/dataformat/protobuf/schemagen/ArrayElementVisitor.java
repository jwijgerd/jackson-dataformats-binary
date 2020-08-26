package com.fasterxml.jackson.dataformat.protobuf.schemagen;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.jsonFormatVisitors.JsonArrayFormatVisitor;
import com.fasterxml.jackson.databind.jsonFormatVisitors.JsonFormatTypes;
import com.fasterxml.jackson.databind.jsonFormatVisitors.JsonFormatVisitable;
import com.fasterxml.jackson.databind.jsonFormatVisitors.JsonFormatVisitorWrapper;
import com.squareup.protoparser.MessageElement;
import com.squareup.protoparser.TypeElement;

public class ArrayElementVisitor extends JsonArrayFormatVisitor.Base implements TypeElementBuilder {
    private MessageElement.Builder _builder;
    private JavaType _type;
    private DefinedTypeElementBuilders _definedTypeElementBuilders;

    public ArrayElementVisitor(SerializerProvider p,
                               JavaType type,
                               DefinedTypeElementBuilders definedTypeElementBuilders) {
        super(p);
        _type = type;
        _definedTypeElementBuilders = definedTypeElementBuilders;
        _builder = MessageElement.builder();
    }

    @Override
    public TypeElement build() {
        return _builder.build();
    }

    @Override
    public void itemsFormat(JsonFormatVisitable visitable, JavaType elementType) throws JsonMappingException {
        visitable.acceptJsonFormatVisitor(new JsonFormatVisitorWrapper.Base(_provider), elementType);
    }
}
