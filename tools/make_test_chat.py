"""Writes a fixed Markdown torture-test chat as app chat JSON (survey/test-chat.json)."""
import json, os, time

MD = r"""# H1 heading
## H2 heading
### H3 heading
#### H4 heading
##### H5 heading
###### H6 heading

Paragraph with **bold**, *italic*, ***both***, ~~strike~~, `inline code`, a [link](https://example.com), an autolink <https://anthropic.com>, and a bare URL https://example.org/path.
Entities: &amp; &lt;tag&gt; &copy; &nbsp;x &mdash; done. Emoji: 🚀✅🎉. Escaped \*not italic\*.
Line one with two trailing spaces@@
line two (hard break).

Inline math $E = mc^2$ and $\frac{a}{b} + \sqrt{x}$ in a sentence. Price is $5 and $10 (not math).

$$
\int_0^1 x^2\,dx = \frac{1}{3}
$$

\[ a^2 + b^2 = c^2 \]

Subscripts $a_1 + a_2 = \alpha_{n}$, Greek $\beta \gamma \Omega$, sum $\sum_{i=1}^{n} i^2$, and \(x \leq y\) inline. Broken $\frac{1}{$ stays text.

$$
\begin{pmatrix} 1 & 0 \\ 0 & 1 \end{pmatrix}
$$

| Formula | Value |
|---|:-:|
| $x^2$ | $\pi r^2$ |

3. Starts at three
4. Four
   - nested bullet in numbered
     1. deep numbered
5. Five with a long line that should wrap nicely onto the next line without the text going under the number marker

- Item with paragraph

  Second paragraph in the same item.
- Item with code:
  ```python
  print("in list")
  ```
- > Quote inside a list

> Outer quote
> > Nested quote
> - list in quote
> ```
> code in quote
> ```

Footnote reference[^1].

[^1]: The footnote text.

![alt text for image](https://example.com/image.png)

| A | B |
|---|---|
| only two | cells |
| missing |

| Wide | table | with | many | columns | to | force | scrolling | sideways |
|---|---|---|---|---|---|---|---|---|
| 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 | 9 |

```
plain fence without language and a very long line that keeps going and going and going past the screen edge for sure
```

```json
{"key": "value", "n": 1, "ok": true, "list": [1, 2, 3]}
```

```diff
- removed line
+ added line
```

    indented code block

***

<details><summary>Click to expand</summary>Hidden text inside details.</details>

Final line.""".replace("@@", "  ")

conv = {
    "id": "test-markdown-0001",
    "title": "Markdown test pass",
    "messages": [
        {"id": "u1", "role": "USER", "text": "Render test"},
        {"id": "a1", "role": "ASSISTANT", "text": MD, "modelId": "anthropic.claude-opus-5-5"},
    ],
    "starred": False,
    "createdAt": int(time.time() * 1000),
    "updatedAt": int(time.time() * 1000),
}
out = os.path.join(os.path.dirname(__file__), "..", "survey", "test-markdown-0001.json")
with open(out, "w", encoding="utf-8") as f:
    json.dump(conv, f, ensure_ascii=False)
print(os.path.abspath(out))
