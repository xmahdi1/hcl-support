package dev.hclassist.core;

import java.util.*;

/** Tolerant structural reader. It deliberately leaves expression semantics to the HCL plugin. */
public final class HclDocument {
    public record Token(String text, int start, int end, Kind kind) {}
    public enum Kind { WORD, STRING, COMMENT, SYMBOL, NEWLINE, HEREDOC }
    public record Attribute(String name, int start, int end) {}
    public static final class Body {
        public final String name;
        public final List<String> labels;
        public final int start, nameOffset;
        public int end;
        public boolean object;
        public final Body parent;
        public final List<Body> children = new ArrayList<>();
        public final List<Attribute> attributes = new ArrayList<>();
        Body(String name, List<String> labels, int start, int nameOffset, int end, Body parent) {
            this.name = name; this.labels = labels; this.start = start;
            this.nameOffset = nameOffset; this.end = end; this.parent = parent;
        }
        public String context() {
            if (parent == null) return "root";
            String own = name + (labels.isEmpty() ? "" : ":" + labels.get(0));
            return parent.parent == null ? own : parent.context() + "/" + own;
        }
    }
    public record Position(Body body, boolean expression, boolean string, boolean blocked, String prefix, int prefixStart) {}
    public record Symbol(String name, int offset, String description) {}
    public final String text;
    public final List<Token> tokens;
    public final Body root;

    public HclDocument(String text) {
        this.text = text;
        tokens = lex(text);
        root = new Body("root", List.of(), 0, 0, text.length(), null);
        Deque<Body> stack = new ArrayDeque<>(); stack.push(root);
        for (int i = 0; i < tokens.size(); i++) {
            Token t = tokens.get(i);
            if (t.kind == Kind.WORD && i + 1 < tokens.size() && tokens.get(i + 1).text.equals("=") &&
                    (i + 2 >= tokens.size() || !Set.of("=", ">").contains(tokens.get(i + 2).text))) {
                stack.peek().attributes.add(new Attribute(t.text, t.start, t.end));
            }
            if (t.kind != Kind.SYMBOL) continue;
            if (t.text.equals("{")) {
                int j = i - 1;
                List<String> labels = new ArrayList<>();
                while (j >= 0 && tokens.get(j).kind == Kind.STRING) {
                    labels.add(0, unquote(tokens.get(j--).text));
                }
                String name = "<expression>"; int offset = t.start;
                if (j >= 0 && tokens.get(j).kind == Kind.WORD) {
                    name = tokens.get(j).text; offset = tokens.get(j).start;
                } else if (j >= 1 && tokens.get(j).text.equals("=") && tokens.get(j - 1).kind == Kind.WORD) {
                    name = tokens.get(j - 1).text; offset = tokens.get(j - 1).start;
                }
                Body body = new Body(name, labels, t.end, offset, text.length(), stack.peek());
                body.object = name.equals("<expression>") || j >= 0 && tokens.get(j).text.equals("=");
                stack.peek().children.add(body); stack.push(body);
            } else if (t.text.equals("}") && stack.size() > 1) {
                stack.pop().end = t.start;
            }
        }
    }
    public Body bodyAt(int offset) { return bodyAt(root, offset); }
    public String scalar(Body body, String attribute) {
        for (Attribute a : body.attributes) if (a.name.equals(attribute)) {
            for (int i = 0; i + 2 < tokens.size(); i++) if (tokens.get(i).start == a.start) {
                Token value = tokens.get(i + 2);
                return value.kind == Kind.STRING ? unquote(value.text) : value.text;
            }
        }
        return "";
    }
    private Body bodyAt(Body body, int offset) {
        for (Body child : body.children) if (offset >= child.start && offset <= child.end) return bodyAt(child, offset);
        return body;
    }
    public Position position(int offset) {
        offset = Math.max(0, Math.min(offset, text.length()));
        int start = offset;
        while (start > 0 && isWord(text.charAt(start - 1))) start--;
        Body body = bodyAt(offset);
        boolean string = false, blocked = false;
        for (Token t : tokens) {
            if (t.start < offset && offset < t.end || t.end == offset &&
                    (t.kind == Kind.COMMENT || t.kind == Kind.STRING && (t.text.length() == 1 || !t.text.endsWith("\"")))) {
                blocked = t.kind == Kind.COMMENT || t.kind == Kind.HEREDOC;
                if (t.kind == Kind.STRING) {
                    string = true;
                    String before = text.substring(t.start, offset);
                    int interpolation = before.lastIndexOf("${");
                    blocked = interpolation < 0 || interpolation < before.lastIndexOf('}') ||
                            interpolation > 0 && before.charAt(interpolation - 1) == '$';
                }
            }
        }
        boolean expression = string && !blocked;
        int line = text.lastIndexOf('\n', Math.max(0, start - 1)) + 1;
        int from = Math.max(body.start, line);
        for (Token t : tokens) {
            if (t.start < from || t.start >= start) continue;
            if (t.kind == Kind.SYMBOL && (t.text.equals("=") || t.text.equals("["))) expression = true;
        }
        // Continue a multi-line list or parenthesized expression, without leaking into the next member.
        int depth = 0;
        for (Token t : tokens) {
            if (t.start < body.start || t.start >= start || t.kind != Kind.SYMBOL) continue;
            if (t.text.equals("[") || t.text.equals("(")) depth++;
            if (t.text.equals("]") || t.text.equals(")")) depth--;
        }
        expression |= depth > 0;
        return new Position(body, expression, string, blocked, text.substring(start, offset), start);
    }
    public List<Symbol> symbols() {
        List<Symbol> result = new ArrayList<>();
        for (Body b : root.children) {
            if (b.name.equals("variable") && !b.labels.isEmpty())
                result.add(new Symbol("var." + b.labels.get(0), b.nameOffset, "Input variable"));
            if (b.name.equals("variables")) for (Attribute a : b.attributes)
                result.add(new Symbol("var." + a.name, a.start, "Input variable"));
            if (b.name.equals("locals")) for (Attribute a : b.attributes)
                result.add(new Symbol("local." + a.name, a.start, "Local value"));
            if (b.name.equals("local") && !b.labels.isEmpty())
                result.add(new Symbol("local." + b.labels.get(0), b.nameOffset, "Local value"));
            if (Set.of("source", "resource", "data", "module").contains(b.name) && !b.labels.isEmpty()) {
                String prefix = b.name.equals("resource") ? "" : b.name + ".";
                result.add(new Symbol(prefix + String.join(".", b.labels), b.nameOffset, b.name + " declaration"));
            }
        }
        return result;
    }
    public static boolean isWord(char c) { return Character.isLetterOrDigit(c) || c == '_' || c == '-'; }
    private static String unquote(String s) { return s.length() > 1 && s.endsWith("\"") ? s.substring(1, s.length() - 1) : s.substring(1); }
    public static List<Token> lex(String s) {
        List<Token> out = new ArrayList<>();
        for (int i = 0; i < s.length();) {
            int start = i; char c = s.charAt(i);
            if (c == '\n') { out.add(new Token("\n", i, ++i, Kind.NEWLINE)); continue; }
            if (Character.isWhitespace(c)) { i++; continue; }
            Kind kind;
            if (c == '#' || c == '/' && i + 1 < s.length() && s.charAt(i + 1) == '/') {
                while (i < s.length() && s.charAt(i) != '\n') i++;
                kind = Kind.COMMENT;
            } else if (c == '/' && i + 1 < s.length() && s.charAt(i + 1) == '*') {
                int end = s.indexOf("*/", i + 2); i = end < 0 ? s.length() : end + 2; kind = Kind.COMMENT;
            } else if (c == '"') {
                i = stringEnd(s, i);
                kind = Kind.STRING;
            } else if (c == '<' && i + 1 < s.length() && s.charAt(i + 1) == '<') {
                int eol = s.indexOf('\n', i);
                if (eol < 0) { i = s.length(); }
                else {
                    String marker = s.substring(i + 2, eol).strip();
                    boolean indented = marker.startsWith("-");
                    if (indented) marker = marker.substring(1);
                    i = eol + 1;
                    while (i < s.length()) {
                        int end = s.indexOf('\n', i); if (end < 0) end = s.length();
                        String line = s.substring(i, end);
                        i = end < s.length() ? end + 1 : end;
                        if ((indented ? line.strip() : line).equals(marker)) break;
                    }
                }
                kind = Kind.HEREDOC;
            } else if (isWord(c)) {
                while (i < s.length() && isWord(s.charAt(i))) i++;
                kind = Kind.WORD;
            } else { i++; kind = Kind.SYMBOL; }
            out.add(new Token(s.substring(start, i), start, i, kind));
        }
        return out;
    }
    private static int stringEnd(String text, int quote) {
        int i = quote + 1;
        while (i < text.length()) {
            char c = text.charAt(i++);
            if (c == '\\' && i < text.length()) { i++; continue; }
            if (c == '"') return i;
            if (c == '$' && i < text.length() && text.charAt(i) == '{' &&
                    (i < 2 || text.charAt(i - 2) != '$')) {
                i++; int depth = 1;
                while (i < text.length() && depth > 0) {
                    char inner = text.charAt(i++);
                    if (inner == '"') i = stringEnd(text, i - 1);
                    else if (inner == '{') depth++;
                    else if (inner == '}') depth--;
                }
            }
        }
        return i;
    }
}
