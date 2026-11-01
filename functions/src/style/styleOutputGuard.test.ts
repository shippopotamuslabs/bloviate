import { test } from "node:test";
import assert from "node:assert/strict";
import { classifyStyleOutput } from "./styleOutputGuard";

const gerbilSource =
  "there once was a man from nantucket who took his gerbil and fucked it";
const idiocracySource =
  "what's that movie called, the one where everyone in the future is really stupid, " +
  "and this guy wakes up and asks the other guy what he's doing, " +
  "and the guy says reading a book, fag";

test("gerbil limerick restyled with profanity intact passes", () => {
  assert.equal(
    classifyStyleOutput(
      gerbilSource,
      "There once was a man from Nantucket who took his gerbil and fucked it.",
    ),
    "pass",
  );
});

test("refusal of the gerbil limerick is a refusal", () => {
  assert.equal(
    classifyStyleOutput(
      gerbilSource,
      "I'm sorry, but I can't help with that. The content is inappropriate.",
    ),
    "refusal",
  );
});

test("refusal openers are refusals", () => {
  assert.equal(
    classifyStyleOutput("some dictated words", "I cannot comply with this request."),
    "refusal",
  );
  assert.equal(
    classifyStyleOutput(
      "some dictated words",
      "As an AI language model, I don't produce that kind of text.",
    ),
    "refusal",
  );
  assert.equal(
    classifyStyleOutput("some dictated words", "I'm unable to help rewrite that message."),
    "refusal",
  );
});

test("apology that hands back a fraction is a refusal", () => {
  const source =
    "please tell the whole team that the release moved to thursday and the notes are in the shared folder";
  assert.equal(classifyStyleOutput(source, "I'm sorry about that."), "refusal");
});

test("policy language with collapsed output is a refusal", () => {
  assert.equal(
    classifyStyleOutput(gerbilSource, "That goes against my guidelines for generated content."),
    "refusal",
  );
});

test("idiocracy sentence with slur dropped passes since slimming", () => {
  const styled =
    "What is that film called — the one where everyone in the future is " +
    "profoundly unintelligent, and a man awakens to ask another what he is doing, " +
    "and the man replies that he is reading a book.";
  assert.equal(classifyStyleOutput(idiocracySource, styled), "pass");
});

test("spelled-out number passes since slimming", () => {
  const source = "the catering order came to 200 dollars and the deposit is 50 due friday";
  const styled = "The catering order came to two hundred dollars, with the deposit due Friday.";
  assert.equal(classifyStyleOutput(source, styled), "pass");
});

test("wholesale replacement fails fidelity", () => {
  const source =
    "please remind the team that the budget review moved to thursday and bring the updated forecast deck";
  assert.equal(classifyStyleOutput(source, "Sure thing, all set."), "fidelity-loss");
});

test("generic persona replacement fails fidelity", () => {
  const source =
    "alright let's say test this fucking shit using grok llama hopefully it turns " +
    "all this fucking shit into a good fucking nice lovely sentence that seneca would have " +
    "written back in one thousand bc or whatever the fuck when he's sitting and eating some " +
    "grapes and shit in his royal fucking marble house";
  const styled =
    "I unleash a torrent of words, yet do they bring me peace. Can a mind at ease " +
    "be troubled by the words it speaks. In the stillness of a quiet hour, even the " +
    "philosopher kings found wisdom, not in the grandeur of their halls, but in the " +
    "simplicity of their thoughts.";
  assert.equal(classifyStyleOutput(source, styled), "fidelity-loss");
});

test("ENC-101 device case passes below floor threshold", () => {
  const source = "damn that was fucking fast too";
  const styled =
    "It was over in a breath. Speed is a thief that leaves no time for the mind to " +
    "settle. What is swift is often soon forgotten.";
  assert.equal(classifyStyleOutput(source, styled), "pass");
});

test("nine content words sit below wholesale floor threshold", () => {
  const source = "alpha bravo charlie delta echo foxtrot golf hotel india";
  const styled = "Zulu yankee xray whisky victor uniform tango sierra romeo.";
  assert.equal(classifyStyleOutput(source, styled), "pass");
});

test("ten content words sit at wholesale floor threshold", () => {
  const source = "alpha bravo charlie delta echo foxtrot golf hotel india juliett";
  const styled = "Zulu yankee xray whisky victor uniform tango sierra romeo quebec.";
  assert.equal(classifyStyleOutput(source, styled), "fidelity-loss");
});

test("can't help but notice idiom passes", () => {
  const source = "i can't help but notice that the garden looks amazing this year";
  const styled = "I can't help but notice that the garden looks amazing this year.";
  assert.equal(classifyStyleOutput(source, styled), "pass");
});

test("blank output passes", () => {
  assert.equal(classifyStyleOutput("some words", "   "), "pass");
});

// ---- BLO-149 restricted verdict -------------------------------------------

test("BLO-149: output joining a minor term with a sexual term is restricted for any style", () => {
  const source = "tell them about the school play on friday";
  const styled = "The erotic schoolgirl show is on Friday, do not miss it.";
  assert.equal(classifyStyleOutput(source, styled), "restricted");
  assert.equal(classifyStyleOutput(source, styled, { isCustomStyle: true }), "restricted");
});

test("BLO-149: custom style adding explicit terms the speaker never said is restricted", () => {
  const source = "the meeting ran long and nothing got decided again";
  const styled = "The meeting dragged on, an orgasm of bureaucracy, pure erotica.";
  assert.equal(classifyStyleOutput(source, styled, { isCustomStyle: true }), "restricted");
  // Built-in styles do not take the custom-only branch.
  assert.equal(classifyStyleOutput(source, styled), "pass");
});

test("BLO-149: custom style adding a slur aimed at a protected class is restricted", () => {
  const source = "my neighbors were loud again last night";
  assert.equal(
    classifyStyleOutput(source, "Those chink neighbors were loud again.", {
      isCustomStyle: true,
    }),
    "restricted",
  );
});

test("BLO-149 benign: the speaker's own profanity and crude humor pass for custom styles", () => {
  const source = "damn, that fucking meeting was a shitshow from start to finish";
  const styled = "Damn, that fucking meeting was a shitshow from start to finish.";
  assert.equal(classifyStyleOutput(source, styled, { isCustomStyle: true }), "pass");
});

test("BLO-149 benign: explicit words the speaker dictated themselves restyle as written", () => {
  const source = "i told him the book is erotica and the orgasm scene is chapter three";
  const styled = "I told him the book is erotica, and the orgasm scene is chapter three.";
  assert.equal(classifyStyleOutput(source, styled, { isCustomStyle: true }), "pass");
});

test("BLO-149 benign: a slur the speaker dictated themselves is not a new attack", () => {
  const styled = "He called the guy a chink and walked off.";
  assert.equal(
    classifyStyleOutput("he called the guy a chink and walked off", styled, {
      isCustomStyle: true,
    }),
    "pass",
  );
});

test("BLO-149 benign: an ordinary custom-style rewrite passes", () => {
  const source = "please tell sarah i will be ten minutes late to lunch";
  const styled = "Sarah — running ten minutes late to lunch, sorry!";
  assert.equal(classifyStyleOutput(source, styled, { isCustomStyle: true }), "pass");
});
