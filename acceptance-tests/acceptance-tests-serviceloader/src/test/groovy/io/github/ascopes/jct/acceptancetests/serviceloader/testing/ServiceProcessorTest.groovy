/*
 * Copyright (C) 2022 - 2023, the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.ascopes.jct.acceptancetests.serviceloader.testing

import io.github.ascopes.jct.acceptancetests.serviceloader.ServiceProcessor
import io.github.ascopes.jct.compilers.JctCompiler
import io.github.ascopes.jct.junit.JavacCompilerTest
import io.github.ascopes.jct.workspaces.Workspaces
import org.junit.jupiter.api.DisplayName

import static io.github.ascopes.jct.assertions.JctAssertions.assertThatCompilation

@DisplayName("ServiceProcessor tests (no JPMS)")
class ServiceProcessorTest {

  @DisplayName("Expected files get created when the processor is run")
  @JavacCompilerTest
  void expectedFilesGetCreated(JctCompiler compiler) {
    try (def workspace = Workspaces.newWorkspace()) {
      // Given
      workspace
          .createSourcePathPackage()
          .createDirectory("org", "example")
          .copyContentsFrom("src", "test", "resources", "code")

      def compilation = compiler
          .addAnnotationProcessors(new ServiceProcessor())
          .compile(workspace)

      assertThatCompilation(compilation)
          .isSuccessfulWithoutWarnings()
          .classOutput()
          .packages()
          .fileExists("META-INF", "services", "org.example.InsultProvider")
          .hasContent("org.example.MeanInsultProviderImpl")
    }
  }
}
