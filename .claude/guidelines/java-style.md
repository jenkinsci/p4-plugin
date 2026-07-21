# Java Style & Formatting

Authoritative source: `IntelliJCodeStyle.xml` at the repo root (import it into
your IDE). The rules below summarise what matters when editing by hand.

## Indentation — tabs, not spaces

- Indentation uses **hard tab characters** (`USE_TAB_CHARACTER = true`,
  `SMART_TABS = true`). Tab width is 4, continuation indent is 8.
- **Never** introduce spaces for indentation. Mixing spaces in a tab-indented
  file is the most common formatting mistake here — match the surrounding lines
  exactly (copy an existing line's leading whitespace if unsure).
- Applies to Java, Groovy, HTML, and Jelly. JSON uses 2-space tabs.

## Imports

- **No wildcard / on-demand imports.** The threshold is set absurdly high
  (50 classes / 10 names), so every import is listed explicitly.
- Don't reorder or "optimise" imports in files you aren't otherwise changing.

## Blank lines & braces

- At most one consecutive blank line inside declarations and code.
- No blank line directly before a closing `}`.
- Opening brace on the same line (K&R), standard for the codebase.

## Naming

- Classes: `PascalCase`. Concrete implementations of an interface use an
  `Impl` suffix where the codebase already does (`P4PasswordImpl`,
  `ManualWorkspaceImpl`, `AutoCleanImpl`, `CommitImpl`).
- Methods/fields: `camelCase`. Constants: `UPPER_SNAKE_CASE`.
- Boolean getters use `isXxx()` (e.g. `isTidy()`), other getters `getXxx()`.

## Logging

- Use `java.util.logging.Logger`:
  ```java
  private static final Logger logger = Logger.getLogger(MyClass.class.getName());
  ```
  (Some older classes declare it non-final as `private static Logger logger`;
  prefer `private static final` for new code but match the file you're in.)

## Jenkins / Stapler idioms (follow these patterns)

- Data-bound config object:
  ```java
  @DataBoundConstructor
  public MyThing(String required) { this.required = required; }

  @DataBoundSetter
  public void setOptional(boolean optional) { this.optional = optional; }
  ```
  Put **required** fields in the `@DataBoundConstructor`; add **optional/new**
  fields via `@DataBoundSetter` so old saved configs still deserialize.
- Keep old constructors as `@Deprecated` overloads that delegate to the new one
  (see `UnshelveBuilder` for the established pattern) — never break binary/config
  compatibility for a published plugin.
- Descriptors are a nested `@Extension public static class DescriptorImpl
  extends ...Descriptor`, annotated with `@Symbol("pipelineName")` for Pipeline.
- Form helpers live on the descriptor: `doCheckXxx(...)` returns `FormValidation`,
  `doFillXxxItems(...)` returns `ListBoxModel`.
- Annotate nullability with `edu.umd.cs.findbugs.annotations.@NonNull` /
  `@CheckForNull` — SpotBugs (`spotbugs:check`) gates the build.

## Static analysis

- `mvn spotbugs:check` must pass. Suppress a genuine false positive narrowly with
  `@SuppressFBWarnings("PATTERN")` and a reason, rather than disabling broadly.
