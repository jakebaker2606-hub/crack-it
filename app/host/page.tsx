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

      <div className="grid grid-cols-2 gap-5">

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

        <button
          onClick={() =>
            updateGame({
              question: "",
            })
          }
          className="bg-yellow-700 p-6 rounded-2xl text-3xl font-black"
        >
          REMOVE QUESTION
        </button>

      </div>

      {/* ANSWERS */}

      <input
        value={answer}
        onChange={(e) => setAnswer(e.target.value)}
        placeholder="Enter Answer"
        className="p-6 rounded-2xl text-black text-3xl"
      />

      <div className="grid grid-cols-2 gap-5">

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

        <button
          onClick={() =>
            updateGame({
              answer: "",
              showAnswer: false,
            })
          }
          className="bg-green-900 p-6 rounded-2xl text-3xl font-black"
        >
          REMOVE ANSWER
        </button>

      </div>

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

        {[
          {
            team: "teamA",
            title: "TEAM A",
            color: "bg-blue-600",
          },
          {
            team: "teamB",
            title: "TEAM B",
            color: "bg-pink-600",
          },
          {
            team: "teamC",
            title: "TEAM C",
            color: "bg-green-600",
          },
        ].map((team) => (

          <div
            key={team.team}
            className={`${team.color} p-6 rounded-3xl flex flex-col gap-4`}
          >

            <h2 className="text-4xl font-black text-center">
              {team.title}
            </h2>

            <button
              onClick={() =>
                updateScore(team.team, 500)
              }
              className="bg-white text-black p-4 rounded-2xl text-2xl font-black"
            >
              +500
            </button>

            <button
              onClick={() =>
                updateScore(team.team, -500)
              }
              className="bg-black p-4 rounded-2xl text-2xl font-black"
            >
              -500
            </button>

          </div>

        ))}

      </div>

      {/* SPECIAL MODES */}

      <div className="grid grid-cols-2 gap-5">

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

        <button
          onClick={() =>
            updateGame({
              showChaosWheel: false,
              chaosResult: "",
            })
          }
          className="bg-purple-900 p-6 rounded-2xl text-3xl font-black"
        >
          HIDE CHAOS ROUND
        </button>

      </div>

      {/* LEADERBOARD */}

      <div className="grid grid-cols-2 gap-5">

        <button
          onClick={() =>
            updateGame({
              showLeaderboard: true,
            })
          }
          className="bg-orange-500 p-6 rounded-2xl text-3xl font-black"
        >
          SHOW LEADERBOARD
        </button>

        <button
          onClick={() =>
            updateGame({
              showLeaderboard: false,
            })
          }
          className="bg-orange-900 p-6 rounded-2xl text-3xl font-black"
        >
          HIDE LEADERBOARD
        </button>

      </div>

      {/* INTRO */}

      <div className="grid grid-cols-2 gap-5">

        <button
          onClick={() =>
            updateGame({
              showIntro: true,
            })
          }
          className="bg-cyan-500 p-6 rounded-2xl text-3xl font-black"
        >
          START INTRO
        </button>

        <button
          onClick={() =>
            updateGame({
              showIntro: false,
            })
          }
          className="bg-cyan-900 p-6 rounded-2xl text-3xl font-black"
        >
          END INTRO
        </button>

      </div>

      {/* WINNER */}

      <div className="grid grid-cols-4 gap-5">

        <button
          onClick={() =>
            updateGame({
              winner: "TEAM A",
            })
          }
          className="bg-blue-700 p-6 rounded-2xl text-2xl font-black"
        >
          TEAM A WINS
        </button>

        <button
          onClick={() =>
            updateGame({
              winner: "TEAM B",
            })
          }
          className="bg-pink-700 p-6 rounded-2xl text-2xl font-black"
        >
          TEAM B WINS
        </button>

        <button
          onClick={() =>
            updateGame({
              winner: "TEAM C",
            })
          }
          className="bg-green-700 p-6 rounded-2xl text-2xl font-black"
        >
          TEAM C WINS
        </button>

        <button
          onClick={() =>
            updateGame({
              winner: "",
            })
          }
          className="bg-gray-700 p-6 rounded-2xl text-2xl font-black"
        >
          HIDE WINNER
        </button>

      </div>

      {/* END GAME */}

      <button
        onClick={() =>
          updateGame({
            showEndGame: true,
          })
        }
        className="bg-red-700 p-6 rounded-2xl text-4xl font-black"
      >
        END GAME
      </button>

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
            showLeaderboard: false,
            showIntro: false,
            showEndGame: false,
            teamA: 0,
            teamB: 0,
            teamC: 0,
          })
        }
        className="bg-white text-black p-6 rounded-2xl text-4xl font-black"
      >
        RESET GAME
      </button>

    </div>

  );

}