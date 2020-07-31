package codes.fendever;

import com.google.auto.service.AutoService;
import com.squareup.javapoet.*;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.tools.Diagnostic;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

class Getter {
    private static final String GETTER_PREFIX = "get";
    private final ExecutableElement backend;

    private Getter(ExecutableElement backend) {
        this.backend = backend;
    }

    private static boolean isGetter(Element element) {
        return element.getKind() == ElementKind.METHOD && element.getSimpleName().toString().startsWith(GETTER_PREFIX);
    }

    public static Getter tryConvert(Element element) {
        if (!isGetter(element)) {
            return null;
        }
        return new Getter((ExecutableElement) element);
    }

    public TypeName getReturnType() {
        return TypeName.get(backend.getReturnType());
    }

    public String getFieldName() {
        return getSimpleName().toString().substring(GETTER_PREFIX.length());
    }

    public Name getSimpleName() {
        return backend.getSimpleName();
    }
}

class Index {
    private final Getter getter;
    private final FieldSpec field;

    Index(Getter getter, FieldSpec field) {
        this.getter = getter;
        this.field = field;
    }

    public Getter getGetter() {
        return getter;
    }

    public FieldSpec getField() {
        return field;
    }
}

@SupportedAnnotationTypes("codes.fendever.CorrelationRepositorySource")
@SupportedSourceVersion(SourceVersion.RELEASE_8)
@AutoService(Processor.class)
public class Proc extends AbstractProcessor {

    private static List<Index> makeIndexes(List<? extends Getter> getters, TypeName enumType) {
        return getters.stream()
                .map(getter -> new Index(getter, makeIndex(getter, enumType)))
                .collect(Collectors.toList());
    }

    private static FieldSpec makeIndex(Getter getter, TypeName enumType) {
        final ParameterizedTypeName fieldType = ParameterizedTypeName.get(
                ClassName.get(Map.class),
                getter.getReturnType(),
                enumType);

        return FieldSpec
                .builder(fieldType, getter.getFieldName() + "Index", Modifier.PRIVATE, Modifier.FINAL, Modifier.STATIC)
                .initializer(CodeBlock.builder()
                        .add("$1T.stream($2T.values()).collect($3T.toMap($2T::$4L, $5T.identity()))",
                                java.util.Arrays.class,
                                enumType,
                                java.util.stream.Collectors.class,
                                getter.getSimpleName(),
                                java.util.function.Function.class)
                        .build())
                .build();
    }

    private static Iterable<MethodSpec> makeLookups(List<Index> getters, TypeName indexableType) {
        return getters.stream()
                .map(index -> makeLookup(index.getGetter(), index.getField(), indexableType))
                .collect(Collectors.toList());
    }

    private static MethodSpec makeLookup(Getter getter, FieldSpec indexField, TypeName indexableType) {
        final ParameterSpec parameter = ParameterSpec.builder(getter.getReturnType(), "code", Modifier.FINAL).build();
        return MethodSpec
                .methodBuilder("findBy" + getter.getFieldName())
                .addParameter(parameter)
                .returns(indexableType)
                .addCode(CodeBlock
                        .builder()
                        .addStatement("return $1N.get($2N)", indexField, parameter)
                        .build())
                .build();
    }

    private static TypeSpec createRepository(TypeElement element) throws IOException {
        List<Getter> getters = element.getEnclosedElements().stream()
                .map(Getter::tryConvert)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        final TypeName indexableType = TypeName.get(element.asType());
        final List<Index> indices = makeIndexes(getters, indexableType);
        return TypeSpec
                .classBuilder(element.getSimpleName() + "Repository")
                .addFields(indices.stream().map(Index::getField).collect(Collectors.toList()))
                .addMethods(makeLookups(indices, indexableType))
                .build();
    }

    @Override
    public boolean process(Set<? extends TypeElement> set, RoundEnvironment roundEnvironment) {
        try {
            for (Element element : roundEnvironment.getElementsAnnotatedWith(CorrelationRepositorySource.class)) {
                final String packageName = ((PackageElement) element.getEnclosingElement()).getQualifiedName().toString();
                JavaFile
                        .builder(packageName, createRepository((TypeElement) element))
                        .build()
                        .writeTo(processingEnv.getFiler());
            }
            return true;
        } catch (IOException ex) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, ex.toString());
            return true;
        }
    }
}
