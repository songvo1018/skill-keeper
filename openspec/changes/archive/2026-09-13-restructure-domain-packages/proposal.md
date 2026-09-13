## Why

`.claude/skills/project-structure-skill` states how this project is to be laid out, and the code does not follow it. An audit against its four rules found: both domain packages hold all 29 of their classes flat, with no `controller`, `service` or `model` sub-package anywhere; three of four services have no interface; and `HelloController` sits outside any domain.

The interface rule also contradicts the archived `add-token-authentication` design, which argued for concrete services with no interface layer. The skill supersedes that rationale; this change retires it.

## What Changes

- Split both domains into the sub-packages the skill now names: `controller`, `service`, `model`, `exception`, `config`, `web`. Each domain's constants class stays in the domain package, since every sub-package uses it.
- Extract an interface per service, naming the role rather than adding an `Impl` suffix: `FileStorage` implemented by `FileStorageService`, `TokenAuthority` implemented by `TokenService`. `CredentialsVerifier` already follows this and is unchanged.
- Leave `FileMetadataStore` a concrete class: it is a collaborator `FileStorageService` owns, not a service, so it gets no interface.
- **BREAKING**: delete `HelloController` and `GET /api/hello` - creation-time scaffolding returning a fixed greeting, no longer wanted and never described by any spec.
- Repoint the auth tests that used `/api/hello` as a stand-in protected endpoint at a real one, so token enforcement stays covered.
- Widen only those members a sibling package can no longer reach, recording what each widening costs.
- Create `util` only when something cross-domain exists; nothing in `src/main` is today.

## Non-Goals

- Any behaviour change beyond removing `GET /api/hello`. The restructure is mechanical; the existing 98 tests are the guarantee and no assertion may be weakened to accommodate a move.
- A second implementation of any extracted interface: they exist because the skill requires them, not because a substitute is planned.
- Renaming the `com.skillskeeper.skillskeeper` package or the `skills-keeper` artifact (review finding 19).
- Splitting test sources into matching sub-packages. Tests move only where a main-code move forces it; the skill's `util` rule names a `src/main` path.
- Findings 14, 15, 17, 18, 26-29 of the review, which remain open.

## Capabilities

### New Capabilities

(none)

### Modified Capabilities

(none - no requirement changes. `GET /api/hello`, the one endpoint removed, appears in no main spec, so there is no delta to write; `skip_specs` is declared, as in the archived `extract-string-literal-constants` change.)

## Impact

- Of the 29 domain classes, 27 move into a sub-package and the two constants classes stay at the domain root as public; two interfaces are added and `HelloController` is deleted. Around 20 test files change imports; five stop using `/api/hello`.
- Spring wiring is unaffected: every new package stays under the `@SpringBootApplication` root, so scanning and `@ConfigurationPropertiesScan` keep working.
- Encapsulation is the real cost: `FileStorageService.initialize()`, `TokenService.retainedCount()`, `FileMetadataStore`, `LoginRequest`'s length constants, `TokenRecord`, `AuthMessages` and two `auth` configurations are package-private on purpose, and several must widen once callers live in a sibling package.
- `.claude/skills/project-structure-skill/SKILL.md` now records the sub-package list and interface-naming rule this change follows, rather than leaving them implied.
