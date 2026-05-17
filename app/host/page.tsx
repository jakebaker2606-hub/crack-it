"use client";

import { useEffect, useState } from "react";

import { db } from "../../lib/firebase";

import {
  ref,
  update,
  onValue,
} from "firebase/database";

export default function HostPage() {

  const [question, setQuestion] = useState("");
  const [answer, setAnswer] = useState("");
  const [timerInput, setTimerInput] = useState(30);

  const [game, setGame] = useState<any>({
    teamA: 0,
    teamB: 0,
    teamC: 0,
  });

  useEffect(() => {

    const gameRef = ref(db, "game");

    const unsubscribe = onValue(gameRef, (snapshot) => {

      const data = snapshot.val();

      if (data) {
        setGame(data);
      }

    });

    return () => unsubscribe();

  }, []);

  const updateGame = async (data: any) => {
    await update(ref(db, "game"), data);
  };

  return (

    <div className="min-h-screen bg-black text-white p-10">

      <h1 className="text-7xl font-black text-yellow-400 text-center mb-10">
        HOST CONTROL PANEL
      </h1>

      <div className="grid gap-5">

        <input
          value={question}
          onChange={(e) => setQuestion(e.target.value)}
          placeholder="Enter Question"
          className="p-5 rounded-2xl text-black text-2xl"
        />

        <button
          onClick={() =>
            updateGame({
              question,
              answer: "",
              showAnswer: false,
            })
          }
          className="bg-yellow-400 text-black p-5 rounded-2xl text-2xl font-black"
        >
          SHOW QUESTION
        </button>

        <button
          onClick={() =>
            updateGame({
              question: "",
            })
          }
          className="bg-yellow-700 p-5 rounded-2xl text-2xl font-black"
        >
          REMOVE QUESTION
        </button>

        <input
          value={answer}
          onChange={(e) => setAnswer(e.target.value)}
          placeholder="Enter Answer"
          className="p-5 rounded-2xl text-black text-2xl"
        />

        <button
          onClick={() =>
            updateGame({
              answer,
              showAnswer: true,
            })
          }
          className="bg-green-500 p-5 rounded-2xl text-2xl font-black"
        >
          SHOW ANSWER
        </button>

        <button
          onClick={() =>
            updateGame({
              answer: "",
              showAnswer: false,
            })
          }
          className="bg-green-900 p-5 rounded-2xl text-2xl font-black"
        >
          REMOVE ANSWER
        </button>

      </div>

      <div className="grid grid-cols-3 gap-5 mt-10">

        <button
          onClick={() =>
            updateGame({
              teamA: game.teamA + 500,
            })
          }
          className="bg-blue-500 p-6 rounded-2xl text-2xl font-black"
        >
          TEAM A +500
        </button>

        <button
          onClick={() =>
            updateGame({
              teamB: game.teamB + 500,
            })
          }
          className="bg-pink-500 p-6 rounded-2xl text-2xl font-black"
        >
          TEAM B +500
        </button>

        <button
          onClick={() =>
            updateGame({
              teamC: game.teamC + 500,
            })
          }
          className="bg-green-500 p-6 rounded-2xl text-2xl font-black"
        >
          TEAM C +500
        </button>

      </div>

      <div className="grid grid-cols-3 gap-5 mt-10">

        <button
          onClick={() =>
            updateGame({
              teamA: Math.max(0, game.teamA - 500),
            })
          }
          className="bg-blue-900 p-6 rounded-2xl text-2xl font-black"
        >
          TEAM A -500
        </button>

        <button
          onClick={() =>
            updateGame({
              teamB: Math.max(0, game.teamB - 500),
            })
          }
          className="bg-pink-900 p-6 rounded-2xl text-2xl font-black"
        >
          TEAM B -500
        </button>

        <button
          onClick={() =>
            updateGame({
              teamC: Math.max(0, game.teamC - 500),
            })
          }
          className="bg-green-900 p-6 rounded-2xl text-2xl font-black"
        >
          TEAM C -500
        </button>

      </div>

      <div className="grid grid-cols-3 gap-5 mt-10">

        <input
          type="number"
          value={timerInput}
          onChange={(e) => setTimerInput(Number(e.target.value))}
          className="p-5 rounded-2xl text-black text-2xl"
        />

        <button
          onClick={() =>
            updateGame({
              timer: timerInput,
              timerRunning: true,
              showTimer: true,
            })
          }
          className="bg-red-500 p-5 rounded-2xl text-2xl font-black"
        >
          START TIMER
        </button>

        <button
          onClick={() =>
            updateGame({
              timerRunning: false,
              showTimer: false,
            })
          }
          className="bg-red-900 p-5 rounded-2xl text-2xl font-black"
        >
          STOP TIMER
        </button>

      </div>

    </div>

  );

}