# Succinct Non-Interactive Zero Knowledge for a von Neumann Architecture

## Scenario

- Client (C) owns public input $x$
- Server (S) owns private input $w$ (witness)
- S computes $z=F(x,w)$ for C
- S is prover, C is verifier

## Conditions on a zk-SNARK proof

- zero-knowledge: Prevent C from learning $w$
- Succinct: $\pi$ has size $O_\lambda(1)$ for a fixed-size security parameter $\lambda$
- Non-interactive: S sends output $z$ and proof $\pi$, no further communication
- ARgument: Convince C that S calculated $z$ correctly
- of Knowledge: ... and that S knows solution

## Universal Circuit Generator

![img](circ_gen.png)

- Universal: Circuit Generator only takes runtime bounds as input, not program
- Circuit: Mathematically emulates CPU for given program+input+witness

## zk-SNARK protocol

![img](zk_snark.png)

- Consists of Key Generator, Prover, Verifier
- This paper: more efficient implementations of each component
- Key Generator (\*once): Takes a circuit $C$ $\to$ generates proving key $pk$ and verification key $vk$
- Prover: Takes proving key $pk$ (which contains the circuit), program $P$, input $x$ and witness $w$ $\to$ generates proof $\pi$
- Verifier: Takes proof $\pi$, program $P$, input $x$ $\to$ accepts if proof matches program execution on input
