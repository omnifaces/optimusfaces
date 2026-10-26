# Contributing

Thanks for your interest in improving OptimusFaces!

## Branch model

- **`develop`** is the active development branch.
  **All pull requests must target `develop`.**
- **`main`** is the release branch.
  It always points at the most recent release, and only maintainers update it.

A pull request from a fork against `main` is automatically retargeted to `develop`.

## Submitting changes

1. Fork the repository.
2. Create a branch off `develop` for your change, e.g. `fix/some-bug`.
3. Make your changes.
   Keep the scope focused: one logical change per pull request.
4. Add or update tests for the changed behavior.
   See [DEVELOPERS.md](DEVELOPERS.md) for how to build and run the tests.
5. Open a pull request with `develop` as the base branch.
6. Describe what changes and *why*.
   Link related issues, e.g. `Closes #123`.

Keep reformatting and renaming out of a pull request with substantive changes.
Open a separate pull request for those.

## AI-assisted contributions

AI coding assistants are welcome.
The same rules apply as to any other contribution, plus these:

- **You are the author.**
  You must understand every line you submit, and be able to explain and defend it in review.
  "The AI wrote it" is not an answer to a review comment.
- **Disclose it.**
  State in the pull request description which tool and model you used, and for what, e.g. "Claude Code with Claude Opus 5.5 wrote the test, I wrote the fix".
  Always include the model name and version, as your tool reports it.
  That keeps the history accountable and traceable, and shows over time which models and versions do the job well.
  A `Co-Authored-By` trailer in the commit message counts as disclosure when it names the model and version, e.g. `Co-Authored-By: Claude Opus 5.5`.
- **Verify it.**
  Build and run the tests locally before opening the pull request.
  Never claim a behavior, a default or a spec rule you have not checked.

Pull requests that ignore these rules may be closed without review.

## Releases

Contributors don't need to touch versions; leave that to the release.
Maintainers cut one as follows.

1. On `develop`, set the project `<version>` in `pom.xml` to `X.Y.Z`, commit it as `Prepare X.Y.Z release`, and tag that commit `X.Y.Z`: lightweight, no `v` prefix.
2. Deploy to Maven Central with `mvn clean deploy -P release`.
   This signs the artifacts with GPG and publishes them automatically.
3. Fast-forward `main` to the release commit.
   Update the version in the `README.md` dependency snippet on `main`, and commit it as `X.Y.Z has been released`.
4. On `develop`, set the version to the next `-SNAPSHOT`, commit it, and merge `main` into `develop`.
5. Push `main`, `develop` and the tag in one go: `git push origin main develop X.Y.Z`.
6. Publish a GitHub Release for the tag:
   ```bash
   gh release create X.Y.Z --verify-tag --title X.Y.Z --latest --notes "..."
   ```
