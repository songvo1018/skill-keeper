## Context

See [proposal.md](proposal.md) for motivation. The rules being applied are in `.claude/skills/project-structure-skill/SKILL.md`, which this change extended with the sub-package list and interface-naming rule decided below, so the convention is written down rather than inferred from a single example.

Today `auth` holds 17 classes and `filestorage` 12 - 29 in total - all directly in their domain package, and `CredentialsVerifier` is the only interface in `src/main`. The code is otherwise in good order: the previous change ([archive/2026-09-13-fix-code-review-defects](../archive/2026-09-13-fix-code-review-defects/proposal.md)) left 98 passing tests, and several classes and members were deliberately kept package-private so a domain's internals stayed internal. That deliberate narrowness is what this restructure has to spend: a sub-package boundary is also a visibility boundary.

This change is mechanical. Its only behavioural edit is deleting an endpoint, and nothing else it touches may alter what the service does.

## Goals / Non-Goals

**Goals:**
- Bring both domains into the layout the skill requires, so the next class written has an obvious home.
- Give each service the interface the skill requires, without inventing abstractions that pretend a second implementation is coming.
- Remove `GET /api/hello` and everything that existed only to serve it.
- Keep the 98 existing tests passing on their current assertions, as proof the restructure changed nothing else.

**Non-Goals:** as in [proposal.md](proposal.md) — no behaviour change beyond the removed endpoint, no second implementation of any interface, no package rename, no test-source restructure, and none of the still-open review findings.

## Decisions

**The interface names the role; the implementation keeps the `Service` suffix.**
The skill's example shows `filestorage/service/FileStorageService.java` as correct, so the concrete class keeps its name and the interface needs a different one. `FileStorage` for the interface implemented by `FileStorageService` follows the precedent already in the codebase: `CredentialsVerifier` is named for what it does, and `AlwaysApprovingCredentialsVerifier` for how. Alternative considered: interface `FileStorageService` with `FileStorageServiceImpl` — rejected, because the skill's own example names the class `FileStorageService`, and `Impl` says nothing a reader did not already know. The skill now records this rule, including `Impl` as a "bad" example.

**`TokenService`'s interface is `TokenAuthority`.**
Applying the rule above needs a role noun for a service that issues tokens, rules on their validity, and resolves one to the username that obtained it. `TokenAuthority` covers all three; `TokenStore` would describe only the bookkeeping, and `Tokens` reads as a collection. This is a derived judgement rather than something the user specified, so it is the one name here worth objecting to if it grates.

**`FileMetadataStore` stays a concrete class with no interface.**
It is a collaborator `FileStorageService` owns — it exists to pin the sidecar format away from the web `ObjectMapper`, and the only reason it is injected at all is so a test can make a metadata write fail. The skill requires interfaces for services, and treating every owned collaborator as a service would multiply interfaces that nothing else can implement. The skill now says this explicitly.

**Six sub-packages, not three.**
The skill names `controller`, `service` and `model`. That leaves exceptions, `@Configuration` classes, the interceptor, the `@RestControllerAdvice` handlers and the request-scoped accessor without a home, and leaving them in the domain package would reproduce exactly the flat layout being removed. `exception`, `config` and `web` are therefore added, and the skill updated to name them. Alternative considered: folding exceptions into `model` — rejected, because a thrown type is not a value the API exchanges.

**Each domain's constants class stays in the domain package.**
`FileStorageMessages` and `AuthMessages` are used from `service`, `web`, `exception` and `controller` alike. Putting them in any one sub-package would make that package a dependency of all the others; keeping them at the domain root, where every sub-package can see them, is what their role already implies. They must become public, which is the price, and it is narrower than the alternatives.

**Visibility widens one member at a time, never wholesale.**
Each of these is package-private today for a stated reason, and each needs a decision rather than a blanket `public`:

| Member | Today | After |
|---|---|---|
| `FileStorageService.initialize()` | package-private `@PostConstruct` | stays package-private if the test that calls it moves to `service`; otherwise public |
| `TokenService.retainedCount()` | test-only probe | same treatment, kept out of the interface either way |
| `FileMetadataStore` + `read`/`write` | internal seam | public within `service`, still absent from `FileStorage` |
| `LoginRequest.MAX_*_LENGTH` | constants beside the field | public, since `@Size` needs them and tests assert them |
| `TokenRecord` | internal to the token store | stays package-private inside `service` |
| `AuthMessages`, `FileStorageMessages` | package-private | public at the domain root |
| `AuthClockConfig`, `ProductionCredentialsVerifierConfiguration` | package-private `@Configuration` | stay package-private inside `config` |

The rule applied: a member only widens if a caller genuinely ends up in a different package. Where a test is the only outside caller, moving that test into the matching sub-package is preferred to widening production API.

**Nothing is extracted into `util` yet.**
The skill sends common classes there, and `src/main` has none: every class belongs to one domain. Creating an empty package to satisfy the letter of the rule would be worse than leaving it absent until something cross-domain exists.

**`HelloController` is deleted rather than rehoused.**
Making it compliant would mean inventing a `hello` domain for a method returning a fixed string. The user has confirmed the functionality is obsolete, and review finding 19 already recommended removing it. Four auth tests use `/api/hello` only as a convenient protected endpoint; they move to `GET /api/files`, which is protected by the same interceptor rule, so token-enforcement coverage is preserved rather than reduced. `AuthTokenInterceptorTest` passes the path as a plain string to a mock request and needs only that string changed.

## Risks / Trade-offs

- [A mechanical move across 29 classes can silently change behaviour if a `@Component` ends up outside the scanned packages] → Every new package stays under `com.skillskeeper.skillskeeper`, so scanning is unaffected; the 98 tests, run after each domain is moved rather than once at the end, are the check.
- [Sub-packages force internals public, weakening the encapsulation the previous change established] → Mitigated by the table above: each widening is decided individually, and moving a test is preferred to widening production code. Some loss is unavoidable and is the accepted cost of the skill's layout.
- [Two interfaces with a single implementation each are abstraction for its own sake] → Accepted: the skill requires them. The proposal records that no second implementation is planned, so a later reader does not go looking for one.
- [Deleting `GET /api/hello` breaks any client calling it] → Declared BREAKING. It returned a fixed greeting and was never specified; the user has confirmed it is obsolete.
- [`TokenAuthority` is a name this change invented] → Flagged in Decisions as the one derived name; cheap to change before implementation, and an `apply` run is the moment to settle it.
- [Reviewing a diff where almost every file moved is hard] → Mitigated by sequencing: `filestorage` first, then `auth`, then the endpoint removal, each a separate step with a green suite, so the history stays reviewable even though the tree does not.
