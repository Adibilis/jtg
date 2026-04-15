package ch.adibilis.jtg.parser;

import ch.adibilis.jtg.config.GeneratorConfig;
import ch.adibilis.jtg.model.endpoints.Endpoint;
import ch.adibilis.jtg.model.endpoints.HttpMethod;
import ch.adibilis.jtg.model.endpoints.PagedEndpoint;
import ch.adibilis.jtg.model.types.*;
import ch.adibilis.jtg.model.types.EnumType;
import ch.adibilis.jtg.pagination.PageParam;
import ch.adibilis.jtg.pagination.PageSizeParam;
import ch.adibilis.jtg.pagination.PagedQuery;
import ch.adibilis.jtg.validation.Validation;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import org.springframework.web.bind.annotation.*;

import java.lang.annotation.Annotation;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.TypeVariable;
import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

public class SpringReflectionParser {

    private final GeneratorConfig config;
    private final Map<String, Type> typeCache = new LinkedHashMap<>();

    private static final Set<String> INT_TYPES = Set.of(
            int.class.getName(), Integer.class.getName(),
            long.class.getName(), Long.class.getName(),
            short.class.getName(), Short.class.getName(),
            byte.class.getName(), Byte.class.getName()
    );

    private static final Set<String> DOUBLE_TYPES = Set.of(
            float.class.getName(), Float.class.getName(),
            double.class.getName(), Double.class.getName(),
            Number.class.getName()
    );

    private static final Set<String> DATE_TYPES = Set.of(
            Date.class.getName(), LocalDate.class.getName(),
            LocalDateTime.class.getName(), Instant.class.getName()
    );

    private static final Set<String> LIST_TYPES = Set.of(
            List.class.getName(), ArrayList.class.getName(),
            Set.class.getName(), HashSet.class.getName(),
            Collection.class.getName(), Iterable.class.getName(),
            "reactor.core.publisher.Flux"
    );

    private static final Set<String> MAP_TYPES = Set.of(
            Map.class.getName(), HashMap.class.getName()
    );

    private static final Set<String> OPTIONAL_TYPES = Set.of(
            Optional.class.getName(), "reactor.core.publisher.Mono"
    );

    private static final Set<String> RESPONSE_ENTITY_TYPES = Set.of(
            "org.springframework.http.ResponseEntity"
    );

    private static final Set<String> IGNORED_PARAM_TYPES = Set.of(
            "jakarta.servlet.http.HttpServletRequest",
            "jakarta.servlet.http.HttpServletResponse",
            "org.springframework.web.server.ServerWebExchange",
            "org.springframework.web.server.WebSession"
    );

    private static final Set<String> IGNORED_PARAM_ANNOTATIONS = Set.of(
            "org.springframework.web.bind.annotation.RequestAttribute",
            "org.springframework.security.core.annotation.AuthenticationPrincipal",
            "org.springframework.web.bind.annotation.ModelAttribute",
            "org.springframework.web.bind.annotation.SessionAttribute"
    );

    private static final String MULTIPART_FILE = "org.springframework.web.multipart.MultipartFile";

    public SpringReflectionParser(GeneratorConfig config) {
        this.config = config;
    }

    public Map<String, Type> getNamedTypes() {
        return Collections.unmodifiableMap(typeCache);
    }

    // --- Public API for type resolution ---

    public Type resolveType(java.lang.reflect.Type type) {
        if (type instanceof Class<?> clazz) {
            return resolveClass(clazz);
        } else if (type instanceof ParameterizedType pt) {
            return resolveParameterized(pt);
        } else if (type instanceof GenericArrayType gat) {
            return new ArrayType(resolveType(gat.getGenericComponentType()));
        } else if (type instanceof TypeVariable<?> tv) {
            return new TypeVar(tv.getName());
        }
        throw new IllegalArgumentException("Unsupported reflect type: " + type);
    }

    // Convenience overload for Class<?> (used in tests)
    public Type resolveType(Class<?> clazz) {
        return resolveClass(clazz);
    }

    private String computeSimpleName(Class<?> clazz) {
        Class<?> enclosing = clazz.getEnclosingClass();
        if (enclosing != null) {
            return enclosing.getSimpleName() + clazz.getSimpleName();
        }
        return clazz.getSimpleName();
    }

    // --- Class resolution ---

    private Type resolveClass(Class<?> clazz) {
        // Arrays
        if (clazz.isArray()) {
            return new ArrayType(resolveClass(clazz.getComponentType()));
        }

        // Custom type mappings
        String simpleName = computeSimpleName(clazz);
        String customMapping = config.customTypeMappings().get(simpleName);
        if (customMapping != null) {
            return mapCustomType(customMapping);
        }

        // Primitives / known types
        Type primitive = resolvePrimitive(clazz);
        if (primitive != null) return primitive;

        // Enums
        if (clazz.isEnum()) {
            return parseEnum(clazz);
        }

        // Objects
        return parseObject(clazz);
    }

    private Type resolvePrimitive(Class<?> clazz) {
        String name = clazz.getName();
        if (INT_TYPES.contains(name)) return PrimitiveType.Int;
        if (DOUBLE_TYPES.contains(name)) return PrimitiveType.Double;
        if (name.equals(BigInteger.class.getName())) return PrimitiveType.BigInt;
        if (name.equals(String.class.getName())) return PrimitiveType.String;
        if (name.equals(boolean.class.getName()) || name.equals(Boolean.class.getName())) return PrimitiveType.Boolean;
        if (name.equals(void.class.getName()) || name.equals(Void.class.getName())) return PrimitiveType.Void;
        if (DATE_TYPES.contains(name)) return PrimitiveType.Date;
        if (name.equals(MULTIPART_FILE)) return PrimitiveType.File;
        return null;
    }

    private Type mapCustomType(String tsType) {
        return switch (tsType) {
            case "string" -> PrimitiveType.String;
            case "number" -> PrimitiveType.Int;
            case "boolean" -> PrimitiveType.Boolean;
            case "bigint" -> PrimitiveType.BigInt;
            default -> PrimitiveType.String; // fallback
        };
    }

    // --- Parameterized type resolution ---

    private Type resolveParameterized(ParameterizedType pt) {
        Class<?> raw = (Class<?>) pt.getRawType();
        String rawName = raw.getName();
        java.lang.reflect.Type[] args = pt.getActualTypeArguments();

        if (LIST_TYPES.contains(rawName)) {
            return new ArrayType(resolveType(args[0]));
        }
        if (MAP_TYPES.contains(rawName)) {
            return new MapType(resolveType(args[0]), resolveType(args[1]));
        }
        if (OPTIONAL_TYPES.contains(rawName)) {
            return new OptionalType(resolveType(args[0]));
        }
        if (RESPONSE_ENTITY_TYPES.contains(rawName)) {
            if (args[0] == Void.class) return PrimitiveType.Void;
            return resolveType(args[0]);
        }

        // Generic object instantiation, e.g. GenericDto<String>
        Type baseType = resolveClass(raw);
        if (baseType instanceof ObjectType baseObj) {
            ObjectType copy = baseObj.copy();
            List<Type> instantiations = new ArrayList<>();
            for (java.lang.reflect.Type arg : args) {
                instantiations.add(resolveType(arg));
            }
            copy.setGenericArgInstantiations(instantiations);
            return copy;
        }
        return baseType;
    }

    // --- Enum parsing ---

    private EnumType parseEnum(Class<?> clazz) {
        String name = computeSimpleName(clazz);
        String packageSegment = extractPackageSegment(clazz);
        List<String> values = Arrays.stream(clazz.getEnumConstants())
                .map(e -> ((Enum<?>) e).name())
                .toList();
        EnumType enumType = new EnumType(name, values, packageSegment);
        typeCache.put(clazz.getName(), enumType);
        return enumType;
    }

    // --- Object parsing ---

    private Type parseObject(Class<?> clazz) {
        String fqn = clazz.getName();

        // Cache check (handles circular references)
        if (typeCache.containsKey(fqn)) {
            return typeCache.get(fqn);
        }

        // Check for @JsonTypeInfo + @JsonSubTypes -> UnionType
        JsonTypeInfo typeInfo = clazz.getAnnotation(JsonTypeInfo.class);
        JsonSubTypes subTypes = clazz.getAnnotation(JsonSubTypes.class);
        if (typeInfo != null && subTypes != null) {
            return parseUnion(clazz, typeInfo, subTypes);
        }

        String simpleName = computeSimpleName(clazz);
        String packageSegment = extractPackageSegment(clazz);
        List<String> genericParams = Arrays.stream(clazz.getTypeParameters())
                .map(TypeVariable::getName)
                .toList();

        ObjectType objectType = new ObjectType(simpleName, packageSegment, genericParams);
        // Register in cache BEFORE parsing fields (circular reference support)
        typeCache.put(fqn, objectType);

        // Detect @JsonNaming
        PropertyNamingStrategy namingStrategy = null;
        JsonNaming jsonNaming = clazz.getAnnotation(JsonNaming.class);
        if (jsonNaming != null) {
            try {
                namingStrategy = jsonNaming.value().getDeclaredConstructor().newInstance();
            } catch (Exception e) {
                throw new RuntimeException("Failed to instantiate naming strategy: " + jsonNaming.value(), e);
            }
        }

        // Parse fields (own + superclass)
        List<Field> fields = new ArrayList<>();
        parseFieldsRecursive(clazz, fields, namingStrategy, Map.of());
        objectType.setFields(fields);

        return objectType;
    }

    private void parseFieldsRecursive(Class<?> clazz, List<Field> fields,
                                       PropertyNamingStrategy namingStrategy,
                                       Map<String, java.lang.reflect.Type> typeBindings) {
        // Superclass first (so inherited fields appear before own fields)
        Class<?> superclass = clazz.getSuperclass();
        if (superclass != null && superclass != Object.class && superclass != Record.class) {
            Map<String, java.lang.reflect.Type> superBindings = new HashMap<>(typeBindings);
            java.lang.reflect.Type genericSuper = clazz.getGenericSuperclass();
            if (genericSuper instanceof ParameterizedType pt) {
                TypeVariable<?>[] params = superclass.getTypeParameters();
                java.lang.reflect.Type[] args = pt.getActualTypeArguments();
                for (int i = 0; i < params.length; i++) {
                    java.lang.reflect.Type resolved = args[i];
                    // If the arg is itself a TypeVariable, check our current bindings
                    if (resolved instanceof TypeVariable<?> tv && typeBindings.containsKey(tv.getName())) {
                        resolved = typeBindings.get(tv.getName());
                    }
                    superBindings.put(params[i].getName(), resolved);
                }
            }

            // Check for @JsonNaming on superclass if not already set
            PropertyNamingStrategy superNaming = namingStrategy;
            if (superNaming == null) {
                JsonNaming jsonNaming = superclass.getAnnotation(JsonNaming.class);
                if (jsonNaming != null) {
                    try {
                        superNaming = jsonNaming.value().getDeclaredConstructor().newInstance();
                    } catch (Exception e) {
                        throw new RuntimeException("Failed to instantiate naming strategy", e);
                    }
                }
            }

            parseFieldsRecursive(superclass, fields, superNaming, superBindings);
        }

        // Own fields
        for (java.lang.reflect.Field javaField : clazz.getDeclaredFields()) {
            if (Modifier.isStatic(javaField.getModifiers())) continue;
            if (javaField.isAnnotationPresent(JsonIgnore.class)) continue;

            // Field name (with @JsonProperty override and @JsonNaming)
            String fieldName = javaField.getName();
            JsonProperty jsonProp = javaField.getAnnotation(JsonProperty.class);
            if (jsonProp != null && !jsonProp.value().isEmpty()) {
                fieldName = jsonProp.value();
            } else if (namingStrategy instanceof PropertyNamingStrategies.NamingBase nb) {
                fieldName = nb.translate(fieldName);
            }

            // Field type (with type variable substitution)
            java.lang.reflect.Type fieldType = javaField.getGenericType();
            if (fieldType instanceof TypeVariable<?> tv && typeBindings.containsKey(tv.getName())) {
                fieldType = typeBindings.get(tv.getName());
            }
            Type resolvedType = resolveType(fieldType);

            // Nullable detection
            boolean required = true;
            for (Annotation ann : javaField.getAnnotations()) {
                String annName = ann.annotationType().getSimpleName();
                if (annName.equals("Nullable")) {
                    required = false;
                    break;
                }
            }

            // Validation constraints
            List<Validation> validations = extractValidations(javaField);

            fields.add(new Field(fieldName, resolvedType, required, validations));
        }
    }

    private List<Validation> extractValidations(java.lang.reflect.Field field) {
        List<Validation> validations = new ArrayList<>();

        for (Annotation ann : field.getAnnotations()) {
            try {
                switch (ann.annotationType().getSimpleName()) {
                    case "Min" -> {
                        long value = (long) ann.annotationType().getMethod("value").invoke(ann);
                        String msg = (String) ann.annotationType().getMethod("message").invoke(ann);
                        if (msg.startsWith("{")) msg = "";
                        validations.add(new Validation.Min(value, msg));
                    }
                    case "Max" -> {
                        long value = (long) ann.annotationType().getMethod("value").invoke(ann);
                        String msg = (String) ann.annotationType().getMethod("message").invoke(ann);
                        if (msg.startsWith("{")) msg = "";
                        validations.add(new Validation.Max(value, msg));
                    }
                    case "Size" -> {
                        int min = (int) ann.annotationType().getMethod("min").invoke(ann);
                        int max = (int) ann.annotationType().getMethod("max").invoke(ann);
                        String msg = (String) ann.annotationType().getMethod("message").invoke(ann);
                        if (msg.startsWith("{")) msg = "";
                        validations.add(new Validation.Size(min, max, msg));
                    }
                    case "NotBlank" -> {
                        String msg = (String) ann.annotationType().getMethod("message").invoke(ann);
                        if (msg.startsWith("{")) msg = "";
                        validations.add(new Validation.NotBlank(msg));
                    }
                    case "Pattern" -> {
                        String regexp = (String) ann.annotationType().getMethod("regexp").invoke(ann);
                        String msg = (String) ann.annotationType().getMethod("message").invoke(ann);
                        if (msg.startsWith("{")) msg = "";
                        validations.add(new Validation.Pattern(regexp, msg));
                    }
                    case "Email" -> {
                        String msg = (String) ann.annotationType().getMethod("message").invoke(ann);
                        if (msg.startsWith("{")) msg = "";
                        validations.add(new Validation.Email(msg));
                    }
                }
            } catch (Exception ignored) {
                // Skip annotations we can't read
            }
        }
        return validations;
    }

    // --- Union parsing ---

    private UnionType parseUnion(Class<?> clazz, JsonTypeInfo typeInfo, JsonSubTypes subTypes) {
        String name = clazz.getSimpleName().replace("$", "");
        String packageSegment = extractPackageSegment(clazz);
        String discriminatorProperty = typeInfo.property();
        List<ObjectType> variants = new ArrayList<>();
        for (JsonSubTypes.Type sub : subTypes.value()) {
            Type variantType = resolveClass(sub.value());
            if (variantType instanceof ObjectType objVariant) {
                // Add discriminator field with literal type value (guard against re-entry from circular refs)
                String discriminatorValue = sub.name().isEmpty() ? sub.value().getSimpleName() : sub.name();
                if (objVariant.getFields().stream().noneMatch(f -> f.name().equals(discriminatorProperty))) {
                    Field discriminatorField = new Field(discriminatorProperty, new LiteralType(discriminatorValue));
                    objVariant.getFields().add(0, discriminatorField);
                }
                variants.add(objVariant);
            }
        }
        UnionType unionType = new UnionType(name, variants, packageSegment, discriminatorProperty);
        typeCache.put(clazz.getName(), unionType);
        return unionType;
    }

    // --- Package segment ---

    private String extractPackageSegment(Class<?> clazz) {
        String pkg = clazz.getPackageName();
        for (String basePackage : config.basePackages()) {
            if (pkg.equals(basePackage)) {
                return "common";
            }
            if (pkg.startsWith(basePackage + ".")) {
                String suffix = pkg.substring(basePackage.length() + 1);
                return suffix.replace('.', '/');
            }
        }
        return "common";
    }

    // --- Endpoint parsing ---

    public List<Endpoint> parseController(Class<?> controllerClass) {
        List<Endpoint> endpoints = new ArrayList<>();
        String classPrefix = "";

        RequestMapping classMapping = controllerClass.getAnnotation(RequestMapping.class);
        if (classMapping != null && classMapping.value().length > 0) {
            classPrefix = classMapping.value()[0];
        }

        String className = controllerClass.getSimpleName();

        List<Method> allMethods = new ArrayList<>();
        Class<?> cls = controllerClass;
        while (cls != null && cls != Object.class) {
            allMethods.addAll(Arrays.asList(cls.getDeclaredMethods()));
            cls = cls.getSuperclass();
        }

        for (Method method : allMethods) {
            if (Modifier.isStatic(method.getModifiers())) continue;

            HttpMethod httpMethod = null;
            String[] paths = {};

            GetMapping get = method.getAnnotation(GetMapping.class);
            PostMapping post = method.getAnnotation(PostMapping.class);
            PutMapping put = method.getAnnotation(PutMapping.class);
            PatchMapping patch = method.getAnnotation(PatchMapping.class);
            DeleteMapping delete = method.getAnnotation(DeleteMapping.class);

            if (get != null) { httpMethod = HttpMethod.GET; paths = get.value(); }
            else if (post != null) { httpMethod = HttpMethod.POST; paths = post.value(); }
            else if (put != null) { httpMethod = HttpMethod.PUT; paths = put.value(); }
            else if (patch != null) { httpMethod = HttpMethod.PATCH; paths = patch.value(); }
            else if (delete != null) { httpMethod = HttpMethod.DELETE; paths = delete.value(); }
            else { continue; }

            if (paths.length == 0) paths = new String[]{""};

            boolean isPaged = method.isAnnotationPresent(PagedQuery.class);

            for (String path : paths) {
                String fullUrl = classPrefix + path;

                Endpoint endpoint;
                if (isPaged) {
                    endpoint = new PagedEndpoint();
                } else {
                    endpoint = new Endpoint();
                }
                endpoint.setClassName(className);
                endpoint.setMethodName(method.getName());
                endpoint.setHttpMethod(httpMethod);
                endpoint.setUrl(fullUrl);

                // Return type
                java.lang.reflect.Type returnType = method.getGenericReturnType();
                endpoint.setReturnType(resolveReturnType(returnType));

                // Parameters
                parseParameters(method, endpoint);

                endpoints.add(endpoint);
            }
        }

        return endpoints;
    }

    private Type resolveReturnType(java.lang.reflect.Type returnType) {
        if (returnType instanceof ParameterizedType pt) {
            Class<?> raw = (Class<?>) pt.getRawType();
            String rawName = raw.getName();

            // Unwrap ResponseEntity
            if (RESPONSE_ENTITY_TYPES.contains(rawName)) {
                java.lang.reflect.Type[] args = pt.getActualTypeArguments();
                if (args[0] == Void.class) return PrimitiveType.Void;
                return resolveReturnType(args[0]);
            }
            // Unwrap Mono
            if (rawName.equals("reactor.core.publisher.Mono")) {
                return resolveReturnType(pt.getActualTypeArguments()[0]);
            }
            // Flux -> ArrayType
            if (rawName.equals("reactor.core.publisher.Flux")) {
                return new ArrayType(resolveType(pt.getActualTypeArguments()[0]));
            }
        }

        if (returnType instanceof Class<?> clazz) {
            // Raw ResponseEntity
            if (RESPONSE_ENTITY_TYPES.contains(clazz.getName())) {
                return PrimitiveType.Void;
            }
        }

        return resolveType(returnType);
    }

    private void parseParameters(Method method, Endpoint endpoint) {
        java.lang.reflect.Parameter[] params = method.getParameters();
        java.lang.reflect.Type[] genericTypes = method.getGenericParameterTypes();
        Annotation[][] paramAnnotations = method.getParameterAnnotations();
        boolean isPaged = endpoint instanceof PagedEndpoint;

        for (int i = 0; i < params.length; i++) {
            java.lang.reflect.Parameter param = params[i];
            Class<?> paramClass = param.getType();

            if (isIgnoredParamType(paramClass)) continue;

            String paramName = param.getName();
            Annotation[] annotations = paramAnnotations[i];

            if (hasIgnoredAnnotation(annotations)) continue;

            boolean isPageParam = false;
            boolean isPageSizeParam = false;

            for (Annotation ann : annotations) {
                if (ann instanceof PageParam) isPageParam = true;
                if (ann instanceof PageSizeParam) isPageSizeParam = true;
            }

            boolean handled = false;
            for (Annotation ann : annotations) {
                if (ann instanceof RequestPart) {
                    endpoint.getFileParams().add(new Field(paramName, PrimitiveType.File));
                    handled = true;
                    break;
                } else if (ann instanceof PathVariable) {
                    endpoint.getUrlArgs().add(new Field(paramName, resolveType(genericTypes[i])));
                    handled = true;
                    break;
                } else if (ann instanceof RequestBody) {
                    endpoint.setBody(resolveType(genericTypes[i]));
                    handled = true;
                    break;
                } else if (ann instanceof RequestParam rp) {
                    Type resolvedType = resolveType(genericTypes[i]);
                    if (resolvedType == PrimitiveType.File) {
                        endpoint.getFileParams().add(new Field(paramName, PrimitiveType.File));
                    } else if (isPaged && isPageParam) {
                        ((PagedEndpoint) endpoint).setPageVariable(paramName);
                    } else if (isPaged && isPageSizeParam) {
                        ((PagedEndpoint) endpoint).setPageSizeVariable(paramName);
                    } else {
                        boolean required = rp.required();
                        endpoint.getParams().add(new Field(paramName, resolvedType, required));
                    }
                    handled = true;
                    break;
                }
            }

            // No mapping annotation → treat as query param
            if (!handled && !hasAnyMappingAnnotation(annotations)) {
                Type resolvedType = resolveType(genericTypes[i]);
                endpoint.getParams().add(new Field(paramName, resolvedType, true));
            }
        }
    }

    private boolean hasAnyMappingAnnotation(Annotation[] annotations) {
        for (Annotation ann : annotations) {
            if (ann instanceof RequestParam || ann instanceof PathVariable ||
                ann instanceof RequestBody || ann instanceof RequestPart ||
                ann instanceof PageParam || ann instanceof PageSizeParam) {
                return true;
            }
        }
        return false;
    }

    // --- Utility ---

    static boolean isIgnoredParamType(Class<?> type) {
        return IGNORED_PARAM_TYPES.contains(type.getName());
    }

    private static boolean hasIgnoredAnnotation(Annotation[] annotations) {
        for (Annotation ann : annotations) {
            if (IGNORED_PARAM_ANNOTATIONS.contains(ann.annotationType().getName())) {
                return true;
            }
        }
        return false;
    }
}
