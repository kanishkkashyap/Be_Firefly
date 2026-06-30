with open("app/src/main/java/com/firefly/befirefly/ui/components/ChatWindow.kt", "r") as f:
    lines = f.readlines()

stack = []
for i, line in enumerate(lines):
    for char in line:
        if char == '{':
            stack.append(i + 1)
        elif char == '}':
            if stack:
                stack.pop()
            else:
                print(f"Extra }} at line {i + 1}")

if stack:
    for line_num in stack:
        print(f"Unmatched {{ at line {line_num}")
