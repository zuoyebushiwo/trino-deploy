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
package io.trino.plugin.base;

import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.FromStringDeserializer;
import io.trino.spi.type.Type;
import io.trino.spi.type.TypeId;
import io.trino.spi.type.TypeManager;

import javax.inject.Inject;
import java.util.function.Function;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;

public final class TypeDeserializer
        extends FromStringDeserializer<Type>
{
    private final Function<TypeId, Type> typeLoader;

    @Inject
    public TypeDeserializer(TypeManager typeManager)
    {
        this(requireNonNull(typeManager, "typeManager is null")::getType);
    }

    public TypeDeserializer(Function<TypeId, Type> typeLoader)
    {
        super(Type.class);
        this.typeLoader = requireNonNull(typeLoader, "typeLoader is null");
    }

    @Override
    protected Type _deserialize(String value, DeserializationContext context)
    {
        Type type = typeLoader.apply(TypeId.of(value));
        checkArgument(type != null, "Unknown type %s", value);
        return type;
    }
}
