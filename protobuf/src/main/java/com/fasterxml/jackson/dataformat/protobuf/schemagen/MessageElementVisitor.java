package com.fasterxml.jackson.dataformat.protobuf.schemagen;

import java.util.HashSet;
import java.util.Set;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.jsonFormatVisitors.JsonFormatVisitable;
import com.fasterxml.jackson.databind.jsonFormatVisitors.JsonObjectFormatVisitor;

import com.fasterxml.jackson.databind.type.MapLikeType;
import com.squareup.protoparser.DataType;
import com.squareup.protoparser.DataType.NamedType;
import com.squareup.protoparser.DataType.ScalarType;
import com.squareup.protoparser.FieldElement;
import com.squareup.protoparser.FieldElement.Label;
import com.squareup.protoparser.MessageElement;
import com.squareup.protoparser.TypeElement;

public class MessageElementVisitor extends JsonObjectFormatVisitor.Base
    implements TypeElementBuilder
{
    protected MessageElement.Builder _builder;

    protected TagGenerator _tagGenerator;

    protected JavaType _type;

    protected Set<JavaType> _nestedTypes = new HashSet<>();

    protected DefinedTypeElementBuilders _definedTypeElementBuilders;

    public MessageElementVisitor(SerializerProvider provider, JavaType type,
            DefinedTypeElementBuilders definedTypeElementBuilders, boolean isNested)
    {
        super(provider);
        _definedTypeElementBuilders = definedTypeElementBuilders;
        _type = type;
        _builder = MessageElement.builder();
        _builder.name(type.getRawClass().getSimpleName());
        _builder.documentation("Message for " + type.toCanonical());
    }

    @Override
    public TypeElement build() {
        return _builder.build();
    }

    @Override
    public void property(BeanProperty writer) throws JsonMappingException {
        _builder.addField(buildFieldElement(writer, Label.REQUIRED));
    }

    @Override
    public void property(String name, JsonFormatVisitable handler, JavaType propertyTypeHint) throws JsonMappingException {
        _builder.addField(buildFieldElement(name, propertyTypeHint, Label.REQUIRED));
    }

    @Override
    public void optionalProperty(BeanProperty writer) throws JsonMappingException {
        _builder.addField(buildFieldElement(writer, Label.OPTIONAL));
    }

    @Override
    public void optionalProperty(String name, JsonFormatVisitable handler, JavaType propertyTypeHint) throws JsonMappingException {
        _builder.addField(buildFieldElement(name, propertyTypeHint, Label.OPTIONAL));
    }

    protected FieldElement buildFieldElement(BeanProperty writer, Label label) throws JsonMappingException
    {
        FieldElement.Builder fBuilder = FieldElement.builder();

        fBuilder.name(writer.getName());
        fBuilder.tag(nextTag(writer));

        JavaType type = writer.getType();

        if (type.isArrayType() || type.isCollectionLikeType()) {
            if (ProtobufSchemaHelper.isBinaryType(type)) {
                fBuilder.label(label);
                fBuilder.type(ScalarType.BYTES);
            } else {
                fBuilder.label(Label.REPEATED);
                fBuilder.type(getDataType(type.getContentType()));
            }
        } else if(type.isMapLikeType()) {
            fBuilder.label(Label.REPEATED);
            fBuilder.type(NamedType.create("MapFieldEntry"));
            if(!_definedTypeElementBuilders.containsBuilderFor(type)) {
                final MapLikeType javaType = (MapLikeType) type;
                final DataType keyType = ProtobufSchemaHelper.getScalarType(javaType.getKeyType());
                if(keyType == null || keyType == ScalarType.DOUBLE || keyType == ScalarType.FLOAT || keyType == ScalarType.BYTES) {
                    throw new IllegalArgumentException("Key of Map must be a scalar type (expect DOUBLE, FLOAT and BYTES)");
                }
                final DataType valueType = ProtobufSchemaHelper.getScalarType(javaType.getContentType()) != null ?
                        ProtobufSchemaHelper.getScalarType(javaType.getContentType()) :
                        DataType.NamedType.create(javaType.getContentType().getRawClass().getSimpleName());
                _definedTypeElementBuilders.addTypeElement(type, new TypeElementBuilder() {
                    @Override
                    public TypeElement build() {
                        return MessageElement.builder()
                                .name("MapFieldEntry")
                                .addField(
                                        FieldElement.builder()
                                                .tag(1)
                                                .label(FieldElement.Label.OPTIONAL)
                                                .name("key")
                                                .type(keyType).build())
                                .addField(
                                        FieldElement.builder()
                                                .tag(2)
                                                .label(FieldElement.Label.OPTIONAL)
                                                .name("value")
                                                .type(valueType).build())
                                .build();
                    }
                }, false);
            }
        } else {
            fBuilder.label(label);
            fBuilder.type(getDataType(type));
            // we need to check whether this is defined as an array by the Serializer implementation
            if(_definedTypeElementBuilders.containsBuilderFor(type) &&
                    _definedTypeElementBuilders.getBuilderFor(type) instanceof ArrayElementVisitor) {
                fBuilder.label(Label.REPEATED);
            }
        }
        return fBuilder.build();
    }

    protected FieldElement buildFieldElement(String name, JavaType type, Label label) throws JsonMappingException {
        FieldElement.Builder fBuilder = FieldElement.builder();

        fBuilder.name(name);
        if(_tagGenerator != null && _tagGenerator instanceof DefaultTagGenerator) {
            fBuilder.tag(((DefaultTagGenerator) _tagGenerator).nextTag());
        } else if(_tagGenerator == null) {
            _tagGenerator = new DefaultTagGenerator();
            fBuilder.tag(((DefaultTagGenerator) _tagGenerator).nextTag());
        } else {
            throw new IllegalStateException("Annotated properties (with 'JsonProperty.index') are not supported");
        }

        if (type.isArrayType() || type.isCollectionLikeType()) {
            if (ProtobufSchemaHelper.isBinaryType(type)) {
                fBuilder.label(label);
                fBuilder.type(ScalarType.BYTES);
            } else {
                fBuilder.label(Label.REPEATED);
                fBuilder.type(getDataType(type.getContentType()));
            }
        } else if(type.isMapLikeType()) {
            fBuilder.label(Label.REPEATED);
            fBuilder.type(NamedType.create("MapFieldEntry"));
        }  else {
            fBuilder.label(label);
            fBuilder.type(getDataType(type));
            // we need to check whether this is defined as an array by the Serializer implementation
            if(_definedTypeElementBuilders.containsBuilderFor(type) &&
                    _definedTypeElementBuilders.getBuilderFor(type) instanceof ArrayElementVisitor) {
                fBuilder.label(Label.REPEATED);
            }
        }
        return fBuilder.build();
    }

    protected int nextTag(BeanProperty writer) {
        getTagGenerator(writer);
        return _tagGenerator.nextTag(writer);
    }

    protected void getTagGenerator(BeanProperty writer) {
        if (_tagGenerator == null) {
            if (ProtobufSchemaHelper.hasIndex(writer)) {
                _tagGenerator = new AnnotationBasedTagGenerator();
            } else {
                _tagGenerator = new DefaultTagGenerator();
            }
        }
    }

    protected DataType getDataType(JavaType type) throws JsonMappingException
    {
        if (!_definedTypeElementBuilders.containsBuilderFor(type)) { // No self ref
            if (isNested(type)) {
                if (!_nestedTypes.contains(type)) { // create nested type
                    _nestedTypes.add(type);
                    ProtoBufSchemaVisitor builder = acceptTypeElement(_provider, type,
                            _definedTypeElementBuilders, true);
                    DataType scalarType = builder.getSimpleType();
                    if (scalarType != null){
                        return scalarType;
                    }
                    _builder.addType(builder.build());
                }
            } else { // track non-nested types to generate them later
                ProtoBufSchemaVisitor builder = acceptTypeElement(_provider, type,
                        _definedTypeElementBuilders, false);
                DataType scalarType = builder.getSimpleType();
                if (scalarType != null){
                    return scalarType;
                }
            }
        }
        return NamedType.create(type.getRawClass().getSimpleName());
    }

    private ProtoBufSchemaVisitor acceptTypeElement(SerializerProvider provider, JavaType type,
            DefinedTypeElementBuilders definedTypeElementBuilders, boolean isNested) throws JsonMappingException
    {
        JsonSerializer<Object> serializer = provider.findValueSerializer(type, null);
        ProtoBufSchemaVisitor visitor = new ProtoBufSchemaVisitor(provider, definedTypeElementBuilders, isNested);
        serializer.acceptJsonFormatVisitor(visitor, type);
        return visitor;
    }

    private boolean isNested(JavaType type)
    {
        Class<?> match = type.getRawClass();
        for (Class<?> cls : _type.getRawClass().getDeclaredClasses()) {
            if (cls == match) {
                return true;
            }
        }
        return false;
    }
}
