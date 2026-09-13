package com.skillskeeper.skillskeeper.filestorage.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;

import com.skillskeeper.skillskeeper.filestorage.model.FileStorageProperties;

import com.skillskeeper.skillskeeper.SkillsKeeperApplication;
import com.skillskeeper.skillskeeper.support.InMemoryStoredFileRepository;
import com.skillskeeper.skillskeeper.support.PostgresTestContainer;

class FileStoragePropertiesTest {

	@TempDir
	Path tempDir;

	@Test
	void bindingTheRecordTouchesNothingOnDisk() {
		Path absent = this.tempDir.resolve("not-created-yet");

		FileStorageProperties properties = new FileStorageProperties(absent);

		assertThat(properties.baseDir()).isEqualTo(absent);
		assertThat(Files.exists(absent)).isFalse();
	}

	@Test
	void theServiceCreatesTheDirectoryWhenItInitializes() {
		Path absent = this.tempDir.resolve("not-created-yet");

		FileStorageService service = new FileStorageService(new FileStorageProperties(absent),
				new InMemoryStoredFileRepository());
		assertThat(Files.exists(absent)).isFalse();

		service.initialize();

		assertThat(Files.isDirectory(absent)).isTrue();
	}

	/**
	 * Loading with a configuration name that does not resolve leaves {@code base-dir} unset, which must
	 * fail by naming the property rather than with a null dereference somewhere later.
	 *
	 * <p>The database connection has to be supplied back by hand: discarding the configuration also
	 * discards it, and the context would then fail over the missing datasource before it ever got
	 * as far as binding {@code base-dir}.
	 */
	@Test
	void absentBaseDirFailsStartupNamingTheProperty() {
		assertThatThrownBy(() -> new SpringApplicationBuilder(SkillsKeeperApplication.class)
				.web(WebApplicationType.NONE)
				.properties("spring.config.name=no-such-configuration")
				.properties(PostgresTestContainer.datasourceProperties())
				.run()
				.close())
				.hasStackTraceContaining("baseDir");
	}
}
