package com.skillskeeper.skillskeeper.auth;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;

import com.skillskeeper.skillskeeper.SkillsKeeperApplication;

/**
 * The stub verifier approves any credentials, so production must not be able to start with it as the
 * only credential check available.
 */
class ProductionProfileTest {

	@TempDir
	Path storageDir;

	@Test
	void productionProfileRefusesToStartWithoutRealCredentialVerification() {
		assertThatThrownBy(() -> new SpringApplicationBuilder(SkillsKeeperApplication.class)
				.web(WebApplicationType.NONE)
				.profiles("prod")
				.properties("app.file-storage.base-dir=" + this.storageDir)
				.run()
				.close())
				.rootCause()
				.hasMessageContaining("AlwaysApprovingCredentialsVerifier");
	}
}
