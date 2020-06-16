### Changes to Google Checkstyle
I don't agree with some rules and therefore have done slight modifications to the ruleset:

- Line length increased from 100 to 160
- Tab width doubled from 2 spaces to 4 spaces
- Removed the requirement for constructor JavaDoc by adding JavadocMethod.tokens without CTOR_DEF
- Made unused imports a checkstyle error
- Allowed for suppression annotations
- Fixed up when upgrading Checkstyle Maven Plugin
- Removed explicit google import ordering
