package org.ndx.codingame.simpleclass;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.PackageDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.Name;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.IntersectionType;
import com.github.javaparser.ast.type.ReferenceType;
import com.github.javaparser.ast.type.UnionType;
import com.github.javaparser.ast.type.WildcardType;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.github.javaparser.printer.configuration.DefaultConfigurationOption;
import com.github.javaparser.printer.configuration.DefaultPrinterConfiguration;

public class PlayerBuilder {
  public class DepdendenciesExtractor extends VoidVisitorAdapter<Void> {
    private class QualifyBuilder {

      /**
       * Add all classes in given package to the qualified names list
       */
      public void allClassesIn(String name) {
        Map<String, String> streamedClasses = packagesToClasses.get(name).stream()
            .collect(Collectors.toMap(
                s -> s,
                s -> name + "." + s));
        qualifying.putAll(streamedClasses);
      }

      public String onlyClass(String name) {
        int lastDot = name.lastIndexOf('.');
        String className = name.substring(lastDot + 1);
        qualifying.put(className, name);
        return className;
      }

      public void allMethodsIn(String stringWithoutComments) {
        throw new UnsupportedOperationException("static asterisk imports are not yet supported, because they are a hell of a ride");
      }

      public void onlyMethod(String name) {
        int lastDot = name.lastIndexOf('.');
        String className = name.substring(0, lastDot);
        String singleClassName = onlyClass(className);
        String methodName = name.substring(lastDot + 1);
        qualifying.put("#" + methodName, className);
      }

    }

    private Collection<String> dependencies = new TreeSet<>();
    /**
     * Map linking short class name to qualified name, in order for dependencies
     * to be added
     */
    private Map<String, String> qualifying = new TreeMap<>();

    public Collection<String> from(CompilationUnit addedClassUnit) {
      addedClassUnit.accept(this, null);
      return dependencies;
    }

    @Override
    public void visit(ImportDeclaration n, Void arg) {
      DefaultPrinterConfiguration configuration = new DefaultPrinterConfiguration();
      configuration.addOption(new DefaultConfigurationOption(DefaultPrinterConfiguration.ConfigOption.PRINT_COMMENTS, false));
      if (n.isStatic()) {
        if (n.isAsterisk()) {
          qualify().allMethodsIn(n.getName().toString(configuration));
        } else {
          qualify().onlyMethod(n.getName().toString(configuration));
        }
      } else {
        if (n.isAsterisk()) {
          qualify().allClassesIn(n.getName().toString(configuration));
        } else {
          qualify().onlyClass(n.getName().toString(configuration));
        }
      }
    }

    @Override
    public void visit(PackageDeclaration n, Void arg) {
      qualify().allClassesIn(n.getName().asString());
      super.visit(n, arg);
    }

    private QualifyBuilder qualify() {
      return new QualifyBuilder();
    }

    private void add(String stringWithoutComments) {
      if (qualifying.containsKey(stringWithoutComments)) {
        dependencies.add(qualifying.get(stringWithoutComments));
      }
    }

    @Override
    public void visit(ClassOrInterfaceDeclaration n, Void arg) {
      super.visit(n, arg);
    }

    @Override
    public void visit(ClassOrInterfaceType n, Void arg) {
      add(n.getName().asString());
      super.visit(n, arg);
    }

    @Override
    public void visit(IntersectionType n, Void arg) {
      super.visit(n, arg);
    }

    @Override
    public void visit(UnionType n, Void arg) {
      super.visit(n, arg);
    }

    @Override
    public void visit(WildcardType n, Void arg) {
      super.visit(n, arg);
    }

    @Override
    public void visit(NameExpr n, Void arg) {
      add(n.getName().asString());
      super.visit(n, arg);
    }

    @Override
    public void visit(MethodCallExpr n, Void arg) {
      String key = "#" + n.getName();
      add(key);
      super.visit(n, arg);
    }
  }

  private Map<String, Set<String>> packagesToClasses;

  public PlayerBuilder(Set<String> keySet) {
    this.packagesToClasses = groupByPackages(keySet);
  }

  /**
   * beware : this supposes all classes to be in packages !
   * 
   * @param keySet
   * @return
   */
  private Map<String, Set<String>> groupByPackages(Set<String> keySet) {
    return keySet.stream()
        .collect(Collectors.groupingBy(this::extractPackageName,
            Collectors.mapping(this::extractClassName, Collectors.toSet())));
  }

  private String extractClassName(String name) {
    int dot = name.lastIndexOf('.');
    if (dot >= 0) {
      return name.substring(dot + 1);
    } else {
      return name;
    }
  }

  private String extractPackageName(String name) {
    int dot = name.lastIndexOf('.');
    if (dot >= 0) {
      return name.substring(0, dot);
    } else {
      return "";
    }
  }

  public CompilationUnit build(Map<String, CompilationUnit> classes, String playerClassFullName) {
    CompilationUnit returned = new CompilationUnit();
    contributeClasses(classes, playerClassFullName, returned, new TreeSet<>());
    return returned;
  }

  /**
   * Recursive method contributing all classes declarations/implementations into
   * generated classes
   * 
   * @param classes
   *          all known classes
   * @param addedClass
   * @param generated
   */
  private void contributeClasses(Map<String, CompilationUnit> classes, String addedClass,
      CompilationUnit generated, Collection<String> processed) {
    if (processed.contains(addedClass))
      return;
    processed.add(addedClass);
    CompilationUnit addedClassUnit = classes.get(addedClass);
    Collection<String> dependencies = findDependenciesIn(addedClassUnit);
    // please do not depend on ourselves !
    dependencies.removeAll(processed);
    for (String dependency : dependencies) {
      if (classes.containsKey(dependency)) {
        contributeClasses(classes, dependency, generated, processed);
      } else {
        addImport(addedClassUnit.getImports(), dependency, generated);
      }
    }
    // Now all classes are contributed, add file inner code
    for (TypeDeclaration d : addedClassUnit.getTypes()) {
      generateType(d, generated);
    }
  }

  private void generateType(TypeDeclaration d, CompilationUnit generated) {
    boolean isAbstract = d.getModifiers().stream().anyMatch(x -> com.github.javaparser.ast.Modifier.Keyword.PUBLIC == x);
    // Setting modifiers to 0 remove the "public" modifier
    d.removeModifier(com.github.javaparser.ast.Modifier.Keyword.PUBLIC);
    if (isAbstract)
      d.addModifier(Modifier.Keyword.ABSTRACT);
    generated.getTypes().add(d);
  }

  private Collection<String> findDependenciesIn(CompilationUnit addedClassUnit) {
    return new DepdendenciesExtractor().from(addedClassUnit);
  }

  private void addImport(List<ImportDeclaration> imports, String dependency, CompilationUnit generated) {
    for (ImportDeclaration importDeclaration : imports) {
      String imported = importDeclaration.getName().toString();
      if (imported.startsWith(dependency)) {
        addImport(importDeclaration, generated);
      }
    }
    addImport(dependency, generated);
  }

  private void addImport(ImportDeclaration importDeclaration, CompilationUnit generated) {
    if (!generated.getImports().contains(importDeclaration)) {
      generated.getImports().add(importDeclaration);
    }
  }

  private void addImport(String d, CompilationUnit generated) {
    for (ImportDeclaration declaration : generated.getImports()) {
      if (d.equals(declaration.getName().toString())) {
        return;
      }
    }
    generated.getImports().add(new ImportDeclaration(new Name(d), false, false));
  }

}
