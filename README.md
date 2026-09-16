# artemis-cli

A CLI tool for artemis that implements common use-cases
that require interaction with the artemis API.


## Features

The tool supports the following use-cases:
- [x] Clone one or more programming submission
- [x] Automatically grade valid text submissions that match a given regex
- [x] List currently locked submissions
- [x] Unlock all or specific locked submissions
- [x] List repository urls for users/submissions

## Building

To build the tool, run the following command:

```bash
$ mvn clean package
```
in the root directory.

The jar will be at `target/artemis-cli.jar`.

## Automatically grading text submissions

The program supports grading text submissions programmatically.
For example like this:
```bash
$ java -jar artemis-cli --course=1 --password=abc123 --username=uxxxx grade-text --exercise=12 --tries=20 --command="python validate_submission.py"
```

The command will then be invoked for each submission where the command is passed
the submission text through stdin, and is expected to output the result of the
evaluation to stderr.

The output must have the following format:
```
{points} - {message}
```
For example the program could print
```
1.0 - The output is correct.
```

Then the script will give the user one point and submit the assessment
with the provided message.

For some assignments it is impossible to predict all possible valid submissions,
therefore your program can do the following:
- exit with code 0 and no output on stdout (this will skip the assessment of that submission)
- exits with any other code will throw an exception
- stdout of the command will be printed by the CLI

If the script lands in a loop or does not finish, it will be terminated after 30 seconds.

An example script can be found in the root of the repository, it is called
`validate_submission.py`.
