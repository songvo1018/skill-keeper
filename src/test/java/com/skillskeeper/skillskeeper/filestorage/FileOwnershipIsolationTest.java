package com.skillskeeper.skillskeeper.filestorage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;

import com.jayway.jsonpath.JsonPath;
import com.skillskeeper.skillskeeper.filestorage.model.FileMetadata;
import com.skillskeeper.skillskeeper.filestorage.repository.StoredFileRepository;
import com.skillskeeper.skillskeeper.filestorage.repository.StoredFileRow;
import com.skillskeeper.skillskeeper.support.AuthenticatedApiTest;

/**
 * A file belongs to whoever uploaded it: two accounts driving the real API must not see each
 * other's files, and a file stored without an owner must reach neither of them.
 */
class FileOwnershipIsolationTest extends AuthenticatedApiTest {

	private static final String OTHER_USERNAME = "bob";

	private static final String OWNERLESS_ID = "ownerless-id";

	private static final String NEVER_ISSUED_ID = "never-issued";

	private static final String REQUESTED_ID_PLACEHOLDER = "<the id that was asked for>";

	@Autowired
	private StoredFileRepository repository;

	/**
	 * Registers the account before logging in; the registration result is not asserted, because one
	 * of these names is the account the base class already created, which answers `409`.
	 */
	private String authHeaderFor(String username) throws Exception {
		String credentials = CREDENTIALS_TEMPLATE.formatted(username, TEST_PASSWORD);

		mockMvc.perform(post("/api/auth/register")
				.contentType(MediaType.APPLICATION_JSON)
				.content(credentials));

		String responseBody = mockMvc.perform(post("/api/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content(credentials))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		return BEARER + JsonPath.<String>read(responseBody, "$.token");
	}

	private String upload(String authHeader, String filename) throws Exception {
		MockMultipartFile file = new MockMultipartFile("file", filename, "text/plain", "content".getBytes());

		String responseBody = mockMvc.perform(multipart("/api/files").file(file)
						.header(HttpHeaders.AUTHORIZATION, authHeader))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return JsonPath.read(responseBody, "$.id");
	}

	private List<String> listedFilenames(String authHeader) throws Exception {
		String responseBody = mockMvc.perform(get("/api/files").header(HttpHeaders.AUTHORIZATION, authHeader))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		return JsonPath.read(responseBody, "$[*].originalFilename");
	}

	@Test
	void eachUserSeesOnlyTheirOwnFiles() throws Exception {
		String mine = authHeaderFor(TEST_USERNAME);
		String theirs = authHeaderFor(OTHER_USERNAME);

		upload(mine, "mine.txt");
		upload(theirs, "theirs.txt");

		assertThat(listedFilenames(mine)).contains("mine.txt").doesNotContain("theirs.txt");
		assertThat(listedFilenames(theirs)).contains("theirs.txt").doesNotContain("mine.txt");
	}

	@Test
	void anotherUsersFileIsNotFound() throws Exception {
		String mine = authHeaderFor(TEST_USERNAME);
		String theirs = authHeaderFor(OTHER_USERNAME);
		String theirFileId = upload(theirs, "theirs.txt");

		mockMvc.perform(get("/api/files/{id}", theirFileId).header(HttpHeaders.AUTHORIZATION, mine))
				.andExpect(status().isNotFound());
	}

	/**
	 * Same status and same body, so the answer cannot be read as confirmation that the id exists.
	 * Each body quotes back the id that was asked for, which says nothing about whether it exists;
	 * that echo is masked before the two are compared.
	 */
	@Test
	void anotherUsersFileAnswersTheSameAsAnIdThatWasNeverIssued() throws Exception {
		String mine = authHeaderFor(TEST_USERNAME);
		String theirFileId = upload(authHeaderFor(OTHER_USERNAME), "theirs.txt");

		MvcResult theirFile = notFoundFor(theirFileId, mine);
		MvcResult unknownId = notFoundFor(NEVER_ISSUED_ID, mine);

		assertThat(theirFile.getResponse().getStatus()).isEqualTo(unknownId.getResponse().getStatus());
		assertThat(withoutTheEchoedId(theirFile, theirFileId))
				.isEqualTo(withoutTheEchoedId(unknownId, NEVER_ISSUED_ID));
	}

	private MvcResult notFoundFor(String id, String authHeader) throws Exception {
		return mockMvc.perform(get("/api/files/{id}", id).header(HttpHeaders.AUTHORIZATION, authHeader))
				.andReturn();
	}

	private static String withoutTheEchoedId(MvcResult result, String id) throws Exception {
		return result.getResponse().getContentAsString().replace(id, REQUESTED_ID_PLACEHOLDER);
	}

	@Test
	void loggingInWithTheNameInAnotherLetterCaseStillFindsTheSameFiles() throws Exception {
		upload(authHeaderFor(TEST_USERNAME), "mine.txt");

		String sameUserShouting = authHeaderFor(TEST_USERNAME.toUpperCase());

		assertThat(listedFilenames(sameUserShouting)).contains("mine.txt");
	}

	/**
	 * The row is written straight to the table: no request can produce a file without an owner any
	 * more, and only a file stored before owners existed looks like this.
	 */
	@Test
	void fileStoredWithoutAnOwnerReachesNobody() throws Exception {
		repository.insert(StoredFileRow.forInsert(
				new FileMetadata(OWNERLESS_ID, "old.txt", "text/plain", 7), null));
		writePayloadFor(OWNERLESS_ID);

		for (String authHeader : List.of(authHeaderFor(TEST_USERNAME), authHeaderFor(OTHER_USERNAME))) {
			mockMvc.perform(get("/api/files").header(HttpHeaders.AUTHORIZATION, authHeader))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$[?(@.id == '%s')]".formatted(OWNERLESS_ID)).isEmpty());
			mockMvc.perform(get("/api/files/{id}", OWNERLESS_ID).header(HttpHeaders.AUTHORIZATION, authHeader))
					.andExpect(status().isNotFound());
		}
	}

	private static void writePayloadFor(String id) throws IOException {
		Files.writeString(storageDir().resolve(id + FileStorageMessages.BIN_FILE_SUFFIX), "content");
	}
}
