### Changes to Google Checkstyle
As some of the rules I don't agree with and can be frustrating to use I have done some slight modifications to the sheet. The changes that I
have made are:

- Line length increased from 100 to 160
- Tab width doubled from 2 spaces to 4 spaces
- Removed the requirement for constructor JavaDoc by adding JavadocMethod.tokens without CTOR_DEF
- Made unused imports a checkstyle error
- Allowed for suppression annotations
- Fixed up when upgrading Checkstyle Maven Plugin
