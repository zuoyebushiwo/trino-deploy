/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.trino.metadata;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import io.airlift.compress.zstd.ZstdCompressor;
import io.airlift.compress.zstd.ZstdDecompressor;
import io.airlift.json.JsonCodec;
import io.airlift.json.JsonCodecFactory;
import io.airlift.json.ObjectMapperProvider;
import io.trino.spi.type.Type;
import io.trino.spi.type.TypeId;
import io.trino.spi.type.TypeSignature;
import io.trino.sql.tree.QualifiedName;
import io.trino.type.TypeDeserializer;
import io.trino.type.TypeSignatureDeserializer;
import io.trino.type.TypeSignatureKeyDeserializer;

import java.util.*;
import java.util.function.Function;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.io.BaseEncoding.base32Hex;
import static java.lang.Math.toIntExact;
import static java.util.Locale.ENGLISH;
import static java.util.Objects.requireNonNull;

public class ResolvedFunction
{
    private static final JsonCodec<ResolvedFunction> SERIALIZE_JSON_CODEC = new JsonCodecFactory().jsonCodec(ResolvedFunction.class);
    private static final String PREFIX = "@";
    private final BoundSignature signature;
    private final FunctionId functionId;
    private final FunctionKind functionKind;
    private final boolean deterministic;
    private final FunctionNullability functionNullability;
    private final Map<TypeSignature, Type> typeDependencies;
    private final Set<ResolvedFunction> functionDependencies;

    @JsonCreator
    public ResolvedFunction(
            @JsonProperty("signature") BoundSignature signature,
            @JsonProperty("id") FunctionId functionId,
            @JsonProperty("functionKind") FunctionKind functionKind,
            @JsonProperty("deterministic") boolean deterministic,
            @JsonProperty("nullability") FunctionNullability functionNullability,
            @JsonProperty("typeDependencies") Map<TypeSignature, Type> typeDependencies,
            @JsonProperty("functionDependencies") Set<ResolvedFunction> functionDependencies)
    {
        this.signature = requireNonNull(signature, "signature is null");
        this.functionId = requireNonNull(functionId, "functionId is null");
        this.functionKind = requireNonNull(functionKind, "functionKind is null");
        this.deterministic = deterministic;
        this.functionNullability = requireNonNull(functionNullability, "nullability is null");
        this.typeDependencies = ImmutableMap.copyOf(requireNonNull(typeDependencies, "typeDependencies is null"));
        this.functionDependencies = ImmutableSet.copyOf(requireNonNull(functionDependencies, "functionDependencies is null"));
        checkArgument(functionNullability.getArgumentNullable().size() == signature.getArgumentTypes().size(), "signature and functionNullability must have same argument count");
    }

    @JsonProperty
    public BoundSignature getSignature()
    {
        return signature;
    }

    @JsonProperty("id")
    public FunctionId getFunctionId()
    {
        return functionId;
    }

    @JsonProperty("functionKind")
    public FunctionKind getFunctionKind()
    {
        return functionKind;
    }

    @JsonProperty
    public boolean isDeterministic()
    {
        return deterministic;
    }

    @JsonProperty
    public FunctionNullability getFunctionNullability()
    {
        return functionNullability;
    }

    @JsonProperty
    public Map<TypeSignature, Type> getTypeDependencies()
    {
        return typeDependencies;
    }

    @JsonProperty
    public Set<ResolvedFunction> getFunctionDependencies()
    {
        return functionDependencies;
    }

    public static boolean isResolved(QualifiedName name)
    {
        return name.getSuffix().startsWith(PREFIX);
    }

    public QualifiedName toQualifiedName()
    {
        byte[] json = SERIALIZE_JSON_CODEC.toJsonBytes(this);

        // json can be large so use zstd to compress
        ZstdCompressor compressor = new ZstdCompressor();
        byte[] compressed = new byte[compressor.maxCompressedLength(json.length)];
        int outputSize = compressor.compress(json, 0, json.length, compressed, 0, compressed.length);

        // names are case insensitive, so use base32 instead of base64
        String base32 = base32Hex().encode(compressed, 0, outputSize);
        // add name so expressions are still readable
        return QualifiedName.of(PREFIX + signature.getName() + PREFIX + base32);
    }

    public static String extractFunctionName(QualifiedName qualifiedName)
    {
        String data = qualifiedName.getSuffix();
        if (!data.startsWith(PREFIX)) {
            return data;
        }
        List<String> parts = Splitter.on(PREFIX).splitToList(data.subSequence(1, data.length()));
        checkArgument(parts.size() == 2, "Expected encoded resolved function to contain two parts: %s", qualifiedName);
        return parts.get(0);
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ResolvedFunction that = (ResolvedFunction) o;
        return Objects.equals(signature, that.signature) &&
                Objects.equals(functionId, that.functionId) &&
                Objects.equals(functionKind, that.functionKind) &&
                deterministic == that.deterministic &&
                Objects.equals(typeDependencies, that.typeDependencies) &&
                Objects.equals(functionDependencies, that.functionDependencies);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(signature, functionId, functionKind, deterministic, typeDependencies, functionDependencies);
    }

    @Override
    public String toString()
    {
        return signature.toString();
    }

    public static class ResolvedFunctionDecoder
    {
        private final JsonCodec<ResolvedFunction> jsonCodec;

        public ResolvedFunctionDecoder(Function<TypeId, Type> typeLoader)
        {
            ObjectMapperProvider objectMapperProvider = new ObjectMapperProvider();
            objectMapperProvider.setJsonDeserializers(ImmutableMap.of(
                    Type.class, new TypeDeserializer(typeLoader),
                    TypeSignature.class, new TypeSignatureDeserializer()));
            objectMapperProvider.setKeyDeserializers(ImmutableMap.of(
                    TypeSignature.class, new TypeSignatureKeyDeserializer()));
            jsonCodec = new JsonCodecFactory(objectMapperProvider).jsonCodec(ResolvedFunction.class);
        }

        public Optional<ResolvedFunction> fromQualifiedName(QualifiedName qualifiedName)
        {
            String data = qualifiedName.getSuffix();
            if (!data.startsWith(PREFIX)) {
                return Optional.empty();
            }
            List<String> parts = Splitter.on(PREFIX).splitToList(data.subSequence(1, data.length()));
            checkArgument(parts.size() == 2, "Expected encoded resolved function to contain two parts: %s", qualifiedName);
            String name = parts.get(0);

            String base32 = parts.get(1);
            // name may have been lower cased, but base32 decoder requires upper case
            base32 = base32.toUpperCase(ENGLISH);
            byte[] compressed = base32Hex().decode(base32);

            byte[] json = new byte[toIntExact(ZstdDecompressor.getDecompressedSize(compressed, 0, compressed.length))];
            new ZstdDecompressor().decompress(compressed, 0, compressed.length, json, 0, json.length);

            ResolvedFunction resolvedFunction = jsonCodec.fromJson(json);
            checkArgument(resolvedFunction.getSignature().getName().equalsIgnoreCase(name),
                    "Expected decoded function to have name %s, but name is %s", resolvedFunction.getSignature().getName(), name);
            return Optional.of(resolvedFunction);
        }
    }
}
