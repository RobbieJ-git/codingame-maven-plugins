package org.ndx.codingame.simpleclass;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseException;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.PackageDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;

import java.io.File;
import java.io.IOException;
import java.util.Optional;

public class ParserUtils {

	public static String getPublicClassFullName(final CompilationUnit playerUnit) {
		final TypeDeclaration playerClass = ParserUtils.getPublicClassIn(playerUnit);
		final Optional<PackageDeclaration> packageObject = playerUnit.getPackageDeclaration();
		if (packageObject.isEmpty()) {
			return playerClass.getName().asString();
		} else {
			final String playerClassName = packageObject.get().getNameAsString() + "." + playerClass.getName();
			return playerClassName;
		}
	}

	public static TypeDeclaration getPublicClassIn(final CompilationUnit playerUnit) {
		final TypeDeclaration playerClass = playerUnit.getTypes().stream()
				.findFirst()
				.filter(t -> {
					boolean public1 = t.getModifiers().stream().anyMatch(x -> com.github.javaparser.ast.Modifier.Keyword.PUBLIC == x.getKeyword());
					if (!public1) {
					  System.out.println(t.getClass().getName());
					}
          return public1; 
				})
				.orElseThrow(() -> new RuntimeException(
						"java source "+playerUnit.getTypes()+" should contain a public class, or else it's impossible for me to assemble them"));
		return playerClass;
	}

	public static CompilationUnit parse(final File f) throws ParseException, IOException {
		JavaParser parser = new JavaParser();
		return ParserUtils.augment(parser.parse(f).getResult().get());
	}

	/**
	 * Add line number comments for easier source mapping
	 * 
	 * @param parse
	 * @return
	 */
	public static CompilationUnit augment(final CompilationUnit parse) {
		return parse;
	}

	public static String getPublicClassFullName(final File playerClass) throws ParseException, IOException {
		return getPublicClassFullName(parse(playerClass));
	}

}
