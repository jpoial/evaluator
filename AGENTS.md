# Agent Guide for Minimalist Software Development

## Objective

Create the smallest clear solution that satisfies the verifiable functional requirements. Prefer simple, working code over a flexible architecture that is not currently needed.

Principle: **KISS — Keep It Simple, Stupid.**

## Core Rules

1. Write only the code required to meet the requirement.
2. Do not predict future requirements or create extension points for them.
3. Avoid duplication, but do not create an abstraction before actual repetition exists.
4. If logic is used in only one place and extracting it does not improve readability, keep it at the point of use.
5. Prefer direct control flow, clear names, and few dependencies.
6. Use an existing solution before adding a new dependency, layer, or utility.
7. Remove dead code, unused imports, temporary workarounds, and unjustified comments. Follow explicit style requirements.
8. Do not modify code unrelated to the task.

## Workflow

### 1. Understand the Requirement

- State the expected behavior and completion criteria.
- Examine the existing code, tests, and project conventions.
- Ask for clarification only when essential behavior cannot be determined.
- Do not treat an assumption as a requirement.

### 2. Choose the Smallest Solution

Before writing code, ask:

- Can the problem be solved by changing existing code?
- What is the smallest number of files and new concepts needed?
- Is a new function, class, interface, configuration option, or dependency currently unavoidable?
- Can the same result be achieved with simpler control flow?

If two solutions satisfy the requirement equally well, choose the shorter and more direct one.

### 3. Implement

- Follow the project's existing style and structure.
- Keep functions focused on one clear responsibility, but do not split code into small functions merely for formality.
- An abstraction must remove actual duplication or make complex logic clearer.
- When needed, a comment should explain why, not restate what the code visibly does.
- Do not add fallback paths, compatibility layers, feature flags, or generalizations without a direct requirement.

### 4. Verify

- Run the relevant tests, static analysis, and formatting checks.
- Test the required behavior and important edge cases.
- Do not duplicate the same assertion in different forms.
- Review the changes and remove everything that does not help satisfy the requirement.

### 5. Report

Briefly describe:

- what changed;
- how the result was verified;
- any requirement-driven limitation or unresolved issue that remains.

Do not propose hypothetical follow-up work or “could be done in the future” suggestions unless requested.

## Abstraction Decision Rule

Create a new abstraction only when at least one condition applies:

- the same substantive logic appears in multiple places;
- extraction makes a complex part significantly easier to understand;
- the framework or a public contract explicitly requires it.

Do not create an abstraction solely because it might be needed someday.

## Dependency Decision Rule

Add a dependency only when:

- the requirement cannot reasonably be met with existing tools;
- the dependency reduces the solution's overall complexity;
- only the necessary part is adopted.

Replacing a few lines of simple code with a large library is not minimalism.

## Definition of Done Checklist

- [ ] All functional requirements are met.
- [ ] The change is likely the smallest reasonable solution.
- [ ] No speculative or unused code remains.
- [ ] Actual duplication is removed without premature abstraction.
- [ ] Names and control flow are clear.
- [ ] New layers and dependencies are unavoidable and justified.
- [ ] Relevant checks pass.
