"use client";

import { useState } from "react";

import { db } from "../../lib/firebase";

import {
  ref,
  set,
  update,
} from "firebase/database";

export default function HostPage() {

  const [question, setQuestion] = useState("");

  const [answer, setAnswer] = useState("");

  return (

    <div className="min-h-screen bg-black text-white p-10 flex flex-col gap-6">

      <h1 className="text-6xl font-black text-yellow-400">
        HOST PANEL
      </h1>

      <input
        value={question}
        onChange={(e) => setQuestion(e.target.value)}
        placeholder="Question"
        className="p-6 text-black rounded-2xl text-3xl"
      />

      <button
        onClick={async () => {

          await update(ref(db, "game"), {
            question: question,
          });

        }}
        className="bg-yellow-500 p-6 rounded-2xl text-3xl font-black"
      >
        SHOW QUESTION
      </button>

      <input
        value={answer}
        onChange={(e) => setAnswer(e.target.value)}
        placeholder="Answer"
        className="p-6 text-black rounded-2xl text-3xl"
      />

      <button
        onClick={async () => {

          await update(ref(db, "game"), {
            answer: answer,
            showAnswer: true,
          });

        }}
        className="bg-green-600 p-6 rounded-2xl text-3xl font-black"
      >
        SHOW ANSWER
      </button>

    </div>

  );

}