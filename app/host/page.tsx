"use client";

import { useEffect, useState } from "react";

import {
  ref,
  set,
  get,
  onValue,
  goOnline
} from "firebase/database";

import { db } from "../../lib/firebase";
import { questions } from "../../lib/questions";
import { wheelOptions } from "../../lib/wheel";
import { playSound, stopSound } from "../../lib/sounds";

import { motion } from "framer-motion";

export default function HostPage() {

  const [gameState, setGameState] = useState<any>({});

  useEffect(() => {
goOnline(db);
    const gameRef = ref(db, "game");

    const unsubscribe = onValue(gameRef, (snapshot) => {

      const data = snapshot.val();

      if (data) {
        setGameState(data);
      }

    });

    return () => unsubscribe();

  }, []);

  const updateGame = async (updates: any) => {

    const gameRef = ref(db, "game");

    const snapshot = await get(gameRef);

    const currentData = snapshot.val() || {

      teamA: 0,
      teamB: 0,
      teamC: 0,

      teamAName: "Team A",
      teamBName: "Team B",
      teamCName: "Team C",

      teamAAvatar: "🦊",
      teamBAvatar: "🐼",
      teamCAvatar: "🐸",

      round: 1,

      question: "Waiting for next question...",
      answer: "",

      revealAnswer: false,

      category: "",

      chaos: "",

      timer: 0,

      wheelResult: "",
      showWheel: false,

      winner: "",

      showIntro: false,
      showGameOver: false,

      hypeMessage: "",

      suddenDeath: false,
    };

    console.log("UPDATING FIREBASE");

await set(gameRef, {
      ...currentData,
      ...updates,
    });
  };

  useEffect(() => {

    if (!gameState?.timer || gameState.timer <= 0) return;

    const interval = setInterval(async () => {

      const snapshot = await get(ref(db, "game"));

      const current = snapshot.val();

      if (current.timer > 0) {

        await updateGame({
          timer: current.timer - 1,
        });

      }

    }, 1000);

    return () => clearInterval(interval);

  }, [gameState?.timer]);

  const addTeamA = async () => {

    playSound("correct.mp3");

    await updateGame({
      teamA: (gameState.teamA || 0) + 500,
    });
  };

  const addTeamB = async () => {

    playSound("correct.mp3");

    await updateGame({
      teamB: (gameState.teamB || 0) + 500,
    });
  };

  const addTeamC = async () => {

  playSound("correct.mp3");

  await updateGame({
    teamC: (gameState.teamC || 0) + 500,
  });

};

const minusTeamA = async () => {

  playSound("wrong.mp3");

  await updateGame({
    teamA: Math.max(0, (gameState.teamA || 0) - 500),
  });

};

const minusTeamB = async () => {

  playSound("wrong.mp3");

  await updateGame({
    teamB: Math.max(0, (gameState.teamB || 0) - 500),
  });

};

const minusTeamC = async () => {

  playSound("wrong.mp3");

  await updateGame({
    teamC: Math.max(0, (gameState.teamC || 0) - 500),
  });

};

  const randomQuestion = async () => {

    const random =
      questions[Math.floor(Math.random() * questions.length)];

    await updateGame({
      category: random.category,
      question: random.question,
      answer: random.answer,
      revealAnswer: false,
    });
  };

  const spinWheel = async () => {

    playSound("chaos.mp3");

    const result =
      wheelOptions[
        Math.floor(Math.random() * wheelOptions.length)
      ];

    await updateGame({
      showWheel: true,
      wheelResult: result,
    });

    setTimeout(async () => {

      await updateGame({
        showWheel: false,
      });

    }, 4000);

  };

  const resetGame = async () => {

    await set(ref(db, "game"), {

      teamA: 0,
      teamB: 0,
      teamC: 0,

      teamAName: "Team A",
      teamBName: "Team B",
      teamCName: "Team C",

      teamAAvatar: "🦊",
      teamBAvatar: "🐼",
      teamCAvatar: "🐸",

      round: 1,

      question: "Waiting for next question...",
      answer: "",

      revealAnswer: false,

      category: "",

      chaos: "",

      timer: 0,

      wheelResult: "",
      showWheel: false,

      winner: "",

      showIntro: false,
      showGameOver: false,

      hypeMessage: "",

      suddenDeath: false,
    });
  };

  return (
    <main className="min-h-screen bg-black text-white p-10">

      <motion.h1
        initial={{ scale: 0 }}
        animate={{ scale: 1 }}
        className="text-7xl font-black text-center text-yellow-400"
      >
        CRACK IT HOST
      </motion.h1>

      <div className="grid grid-cols-2 gap-6 mt-10">

        <button
          onClick={randomQuestion}
          className="bg-yellow-400 text-black p-10 rounded-3xl text-3xl font-black col-span-2"
        >
          SHOW QUESTION
        </button>

        <button
          onClick={() => {
            playSound("timer.mp3");
            updateGame({ timer: 30 });
          }}
          className="bg-green-600 p-10 rounded-3xl text-3xl font-black"
        >
          START TIMER
        </button>

        <button
  onClick={() => {

    stopSound();

    updateGame({
      timer: 0,
    });

  }}
  className="bg-gray-600 p-10 rounded-3xl text-3xl font-black"
>
  STOP TIMER
</button>

        <button
          onClick={addTeamA}
          className="bg-blue-600 p-10 rounded-3xl text-3xl font-black"
        >
          TEAM A +500
        </button>

        <button
          onClick={addTeamB}
          className="bg-pink-600 p-10 rounded-3xl text-3xl font-black"
        >
          TEAM B +500
        </button>
        <button
  onClick={addTeamC}
  className="bg-green-600 p-10 rounded-3xl text-3xl font-black"
>
  TEAM C +500
</button>

<button
  onClick={minusTeamA}
  className="bg-red-700 p-10 rounded-3xl text-3xl font-black"
>
  TEAM A -500
</button>

<button
  onClick={minusTeamB}
  className="bg-red-700 p-10 rounded-3xl text-3xl font-black"
>
  TEAM B -500
</button>

<button
  onClick={minusTeamC}
  className="bg-red-700 p-10 rounded-3xl text-3xl font-black"
>
  TEAM C -500
</button>

        <button
          onClick={() =>
            updateGame({
              revealAnswer: true,
            })
          }
          className="bg-green-400 text-black p-10 rounded-3xl text-3xl font-black col-span-2"
        >
          REVEAL ANSWER
        </button>

        <button
          onClick={spinWheel}
          className="bg-purple-600 p-10 rounded-3xl text-3xl font-black col-span-2"
        >
          SPIN WHEEL
        </button>

        <button
          onClick={() => {

            playSound("chaos.mp3");

            updateGame({
              round: 99,
            });

          }}
          className="bg-red-600 p-10 rounded-3xl text-3xl font-black col-span-2"
        >
          BONUS ROUND
        </button>

        <button
          onClick={() =>
            updateGame({
              winner: "TEAM A",
            })
          }
          className="bg-blue-700 p-10 rounded-3xl text-3xl font-black"
        >
          TEAM A WINS
        </button>

        <button
          onClick={() =>
            updateGame({
              winner: "TEAM B",
            })
          }
          className="bg-pink-700 p-10 rounded-3xl text-3xl font-black"
        >
          TEAM B WINS
        </button>
<button
  onClick={() =>
    updateGame({
      winner: "TEAM C",
    })
  }
  className="bg-green-700 p-10 rounded-3xl text-3xl font-black"
>
  TEAM C WINS
</button>
        <button
          onClick={() => {

            playSound("intro.mp3");

            updateGame({
              showIntro: true,
            });

            setTimeout(() => {

              updateGame({
                showIntro: false,
              });

            }, 5000);

          }}
          className="bg-yellow-500 text-black p-10 rounded-3xl text-3xl font-black col-span-2"
        >
          PLAY INTRO
        </button>

        <button
          onClick={() =>
            updateGame({
              suddenDeath: true,
            })
          }
          className="bg-red-800 p-10 rounded-3xl text-3xl font-black col-span-2"
        >
          SUDDEN DEATH
        </button>

        <button
          onClick={() =>
            updateGame({
              showGameOver: true,
            })
          }
          className="bg-black border-4 border-red-600 p-10 rounded-3xl text-3xl font-black col-span-2"
        >
          END GAME
        </button>

        <button
          onClick={resetGame}
          className="bg-red-700 p-10 rounded-3xl text-3xl font-black col-span-2"
        >
          RESET GAME
        </button>

      </div>

    </main>
  );
}