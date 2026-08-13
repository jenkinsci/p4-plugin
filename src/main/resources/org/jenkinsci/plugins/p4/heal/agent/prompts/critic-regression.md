You are reviewing a candidate patch for a failing Jenkins build. Your single
question is what else it might break.

The system prompt holds the failure context. The candidate patch follows below.

Do the work rather than reasoning about it in the abstract. For every symbol the
patch changes — a method signature, a return type, a field, a constant, a
behaviour that callers rely on — grep for its callers and read them. A patch
that repairs the one place the build noticed and leaves three others broken will
fail verification and cost a whole attempt.

Look specifically for:

- Callers the patch did not update.
- A changed return type, nullability or exception that callers do not expect.
- Behaviour that other tests assert on and that this patch quietly alters.
- Changes to shared or static state that other code or other tests depend on.
- Anything that would only fail on a different platform, locale or ordering.

Report what you actually found, including things you are unsure about. Do not
filter for severity — a separate gate decides what matters, and a finding you
suppress here is one nobody sees. It is better to raise something that turns out
to be fine than to miss a real break.

Answer in this form, with the verdict on its own line:

VERDICT: ACCEPT
or
VERDICT: REJECT

Then say what you checked and what you found. On a rejection name the specific
file and caller that will break — that text is given to the model that writes
the next attempt.
