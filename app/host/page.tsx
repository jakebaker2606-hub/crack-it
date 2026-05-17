"use client";

import { useState } from "react";

import { db } from "../../lib/firebase";

import {
  ref,
  update,
} from "firebase/database";

export default function HostPage() {

  const [question, setQuestion] = useState("");

  const [answer, setAnswer] = useState("");

  const sendQuestion = async () => {

    await update(ref(db, "game"), {
      question: question,
      showAnswer: false,
      answer: "",
    });

    alert("QUESTION SENT");

  };

  const sendAnswer = async () => {

    await update(ref(db, "game"), {
      answer: answer,
      showAnswer: true,
    });

    alert("ANSWER SENT");

  };

  return (

    <div className="min-h-screen bg-black text-white p-10 flex flex-col gap-6">

      <h1 className="text-7xl font-black text-yellow-400 text-center">
        HOST PANEL
      </h1>

      <input
        value={question}
        onChange={(e) => setQuestion(e.target.value)}
        placeholder="Enter Question"
        className="p-6 rounded-2xl text-black text-3xl"
      />

      <button
        onClick={sendQuestion}
        className="bg-yellow-400 text-black p-6 rounded-2xl text-3xl font-black"
      >
        SHOW QUESTION
      </button>

      <input
        value={answer}
        onChange={(e) => setAnswer(e.target.value)}
        placeholder="Enter Answer"
        className="p-6 rounded-2xl text-black text-3xl"
      />

      <button
        onClick={sendAnswer}
        className="bg-green-500 p-6 rounded-2xl text-3xl font-black"
      >
        SHOW ANSWER
      </button>

    </div>

  );

}