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

  const [gameState, setGameState] = useState<any>({
    teamA: 0,
    teamB: 0,
    teamC: 0,
  });

  useEffect(() => {

    const gameRef = ref(db, "game");

    const unsubscribe = onValue(gameRef, (snapshot) => {

      const data = snapshot.val();

      if (data) {
        setGameState(data);
      }

    });

    return () => unsubscribe();

  }, []);

  return (

    <div className="min-h-screen bg-black text-white p-10">

      <h1 className="text-7xl font-black text-center text-yellow-400 mb-10">
        HOST CONTROL PANEL
      </h1>

      {/* QUESTIONS */}

      <div className="grid gap-5 mb-10">

        <input
          value={question}
          onChange={(e) => setQuestion(e.target.value)}
          placeholder="Enter Question"
          className="p-6 rounded-2xl text-black text-3xl font-bold"
        />

        <button
          onClick={async () => {

            await update(ref(db, "game"), {
              question: question,
              answer: "",
              showAnswer: false,
            });

          }}
          className="bg-yellow-500 text-black p-6 rounded-2xl text-3xl font-black"
        >
          SHOW QUESTION
        </button>

        <button
          onClick={async () => {

            await update(ref(db, "game"), {
              question: "",
            });

          }}
          className="bg-yellow-800 p-6 rounded-2xl text-3xl font-black"
        >
          REMOVE QUESTION
        </button>

        {/* ANSWERS */}

        <input
          value={answer}
          onChange={(e) => setAnswer(e.target.value)}
          placeholder="Enter Answer"
          className="p-6 rounded-2xl text-black text-3xl font-bold"
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

        <button
          onClick={async () => {

            await update(ref(db, "game"), {
              answer: "",
              showAnswer: false,
            });

          }}
          className="bg-green-900 p-6 rounded-2xl text-3xl font-black"
        >
          REMOVE ANSWER
        </button>

      </div>

      {/* TIMER */}

      <div className="grid grid-cols-3 gap-5 mb-10">

        <input
          type="number"
          value={timerInput}
          onChange={(e) =>
            setTimerInput(Number(e.target.value))
          }
          className="p-6 rounded-2xl text-black text-3xl font-bold"
        />

        <button
          onClick={async () => {

            await update(ref(db, "game"), {
              timer: timerInput,
              timerRunning: true,
              showTimer: true,
            });

          }}
          className="bg-blue-600 p-6 rounded-2xl text-3xl font-black"
        >
          START TIMER
        </button>

        <button
          onClick={async () => {

            await update(ref(db, "game"), {
              timerRunning: false,
              showTimer: false,
            });

          }}
          className="bg-red-700 p-6 rounded-2xl text-3xl font-black"
        >
          STOP TIMER
        </button>

      </div>

      {/* TEAMS */}

      <div className="grid grid-cols-2 gap-5 mb-10">

        <button
          onClick={() =>
            update(ref(db, "game"), {
              teamA: gameState.teamA + 500,
            })
          }
          className="bg-blue-600 p-8 rounded-3xl text-3xl font-black"
        >
          TEAM A +500
        </button>

        <button
          onClick={() =>
            update(ref(db, "game"), {
              teamA: Math.max(0, gameState.teamA - 500),
            })
          }
          className="bg-blue-900 p-8 rounded-3xl text-3xl font-black"
        >
          TEAM A -500
        </button>

        <button
          onClick={() =>
            update(ref(db, "game"), {
              teamB: gameState.teamB + 500,
            })
          }
          className="bg-pink-600 p-8 rounded-3xl text-3xl font-black"
        >
          TEAM B +500
        </button>

        <button
          onClick={() =>
            update(ref(db, "game"), {
              teamB: Math.max(0, gameState.teamB - 500),
            })
          }
          className="bg-pink-900 p-8 rounded-3xl text-3xl font-black"
        >
          TEAM B -500
        </button>

        <button
          onClick={() =>
            update(ref(db, "game"), {
              teamC: gameState.teamC + 500,
            })
          }
          className="bg-green-600 p-8 rounded-3xl text-3xl font-black"
        >
          TEAM C +500
        </button>

        <button
          onClick={() =>
            update(ref(db, "game"), {
              teamC: Math.max(0, gameState.teamC - 500),
            })
          }
          className="bg-green-900 p-8 rounded-3xl text-3xl font-black"
        >
          TEAM C -500
        </button>

      </div>

    </div>

  );

}