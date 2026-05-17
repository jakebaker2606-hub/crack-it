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

  const updateScore = async (
    team: string,
    amount: number
  ) => {

    const current = game[team] || 0;

    await updateGame({
      [team]: Math.max(0, current + amount),
    });

  };

  return (

    <div className="min-h-screen bg-black text-white p-10 flex flex-col gap-6">

      <h1 className="text-7xl font-black text-yellow-400 text-center">
        HOST PANEL
      </h1>

      {/* QUESTIONS */}

      <input
        value={question}
        onChange={(e) => setQuestion(e.target.value)}
        placeholder="Enter Question"
        className="p-6 rounded-2xl text-black text-3xl"
      />

      <button
        onClick={() =>
          updateGame({
            question,
            answer: "",
            showAnswer: false,
          })
        }
        className="bg-yellow-400 text-black p-6 rounded-2xl text-3xl font-black"
      >
        SHOW QUESTION
      </button>

      {/* ANSWERS */}

      <input
        value={answer}
        onChange={(e) => setAnswer(e.target.value)}
        placeholder="Enter Answer"
        className="p-6 rounded-2xl text-black text-3xl"
      />

      <button
        onClick={() =>
          updateGame({
            answer,
            showAnswer: true,
          })
        }
        className="bg-green-500 p-6 rounded-2xl text-3xl font-black"
      >
        SHOW ANSWER
      </button>

      {/* TIMER */}

      <div className="grid grid-cols-3 gap-5">

        <input
          type="number"
          value={timerInput}
          onChange={(e) =>
            setTimerInput(Number(e.target.value))
          }
          className="p-6 rounded-2xl text-black text-3xl"
        />

        <button
          onClick={() =>
            updateGame({
              timer: timerInput,
              timerRunning: true,
              showTimer: true,
            })
          }
          className="bg-red-500 p-6 rounded-2xl text-3xl font-black"
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
          className="bg-red-900 p-6 rounded-2xl text-3xl font-black"
        >
          STOP TIMER
        </button>

      </div>

      {/* SCORES */}

      <div className="grid grid-cols-3 gap-5">

        <div className="bg-blue-600 p-6 rounded-3xl flex flex-col gap-4">

          <h2 className="text-4xl font-black text-center">
            TEAM A
          </h2>

          <button
            onClick={() => updateScore("teamA", 500)}
            className="bg-white text-black p-4 rounded-2xl text-2xl font-black"
          >
            +500
          </button>

          <button
            onClick={() => updateScore("teamA", -500)}
            className="bg-black p-4 rounded-2xl text-2xl font-black"
          >
            -500
          </button>

        </div>

        <div className="bg-pink-600 p-6 rounded-3xl flex flex-col gap-4">

          <h2 className="text-4xl font-black text-center">
            TEAM B
          </h2>

          <button
            onClick={() => updateScore("teamB", 500)}
            className="bg-white text-black p-4 rounded-2xl text-2xl font-black"
          >
            +500
          </button>

          <button
            onClick={() => updateScore("teamB", -500)}
            className="bg-black p-4 rounded-2xl text-2xl font-black"
          >
            -500
          </button>

        </div>

        <div className="bg-green-600 p-6 rounded-3xl flex flex-col gap-4">

          <h2 className="text-4xl font-black text-center">
            TEAM C
          </h2>

          <button
            onClick={() => updateScore("teamC", 500)}
            className="bg-white text-black p-4 rounded-2xl text-2xl font-black"
          >
            +500
          </button>

          <button
            onClick={() => updateScore("teamC", -500)}
            className="bg-black p-4 rounded-2xl text-2xl font-black"
          >
            -500
          </button>

        </div>

      </div>

      {/* CHAOS ROUND */}

      <button
        onClick={() =>
          updateGame({
            showChaosWheel: true,
            chaosResult: "",
          })
        }
        className="bg-purple-600 p-6 rounded-2xl text-3xl font-black"
      >
        START CHAOS ROUND
      </button>

      {/* WINNER */}

      <div className="grid grid-cols-3 gap-5">

        <button
          onClick={() =>
            updateGame({
              winner: "TEAM A",
            })
          }
          className="bg-blue-700 p-6 rounded-2xl text-3xl font-black"
        >
          TEAM A WINS
        </button>

        <button
          onClick={() =>
            updateGame({
              winner: "TEAM B",
            })
          }
          className="bg-pink-700 p-6 rounded-2xl text-3xl font-black"
        >
          TEAM B WINS
        </button>

        <button
          onClick={() =>
            updateGame({
              winner: "TEAM C",
            })
          }
          className="bg-green-700 p-6 rounded-2xl text-3xl font-black"
        >
          TEAM C WINS
        </button>

      </div>

      {/* RESET */}

      <button
        onClick={() =>
          updateGame({
            question: "",
            answer: "",
            showAnswer: false,
            showTimer: false,
            timerRunning: false,
            timer: 30,
            showChaosWheel: false,
            chaosResult: "",
            winner: "",
            teamA: 0,
            teamB: 0,
            teamC: 0,
          })
        }
        className="bg-white text-black p-6 rounded-2xl text-3xl font-black"
      >
        RESET GAME
      </button>

    </div>

  );

}